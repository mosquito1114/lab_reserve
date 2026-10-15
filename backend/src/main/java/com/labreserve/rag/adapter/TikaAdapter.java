package com.labreserve.rag.adapter;

import com.labreserve.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析适配器（EXTRACT Step 的具体实现）。
 *
 * <p>基于 Apache Tika 2.x，<b>一份依赖覆盖</b> PDF / DOCX / MD / TXT / PPT / XLSX：
 * <ul>
 *   <li>输入：{@link InputStream} + 文件名（Tika 用文件名嗅探格式）</li>
 *   <li>输出：{@link Parsed} —— 文本 + 页数（可能为 null）+ 内容类型 + 字符数</li>
 * </ul>
 *
 * <p>特点：
 * <ul>
 *   <li>无状态，{@link TikaConfig} 单例（{@code @PostConstruct} 初始化一次）</li>
 *   <li>正文最大字符数 {@value #MAX_CONTENT_CHARS}，超过则截断（防 OOM）；截断时打 warn</li>
 *   <li>解析失败抛 {@link BizException}(500)，由 Step 捕获并标 FAILED</li>
 *   <li>本类<b>不直接读 OSS</b>——只接收已开好的 {@code InputStream}，由 Step 负责把 URL 翻成流</li>
 * </ul>
 *
 * @see <a href="https://tika.apache.org/2.9.2/">Apache Tika 2.x</a>
 */
@Slf4j
@Component
public class TikaAdapter {

    /** 单条文档解析的最大字符数（2 百万字足够一本学术 PDF） */
    private static final int MAX_CONTENT_CHARS = 2_000_000;

    private TikaConfig config;

    @PostConstruct
    void init() {
        try {
            this.config = new TikaConfig();
            log.info("[RAG] TikaAdapter 就绪（基于 Apache Tika 2.x，单条文本上限 {} chars）", MAX_CONTENT_CHARS);
        } catch (Exception e) {
            // Tika 配置失败是启动期致命问题，直接挂
            throw new IllegalStateException("Tika 初始化失败", e);
        }
    }

    /**
     * 把输入流解析成纯文本 + 元数据。
     *
     * @param in       文档字节流（调用方负责关闭；本方法内会自关闭）
     * @param filename 文件名（用于 Tika 内容嗅探；为 null 也能跑，但纯靠 magic 字节）
     * @return 解析结果（非 null）
     * @throws BizException 500 解析失败
     */
    public Parsed parse(InputStream in, String filename) {
        BizException.assertTrue(in != null, "文档流不能为空");
        Metadata metadata = new Metadata();
        // 优先用文件名做格式识别（命中率比纯 magic 高）
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_CHARS);
        AutoDetectParser parser = new AutoDetectParser(config);

        try (TikaInputStream stream = TikaInputStream.get(in)) {
            parser.parse(stream, handler, metadata, new ParseContext());
        } catch (IOException | TikaException | SAXException e) {
            log.error("[RAG] Tika 解析失败: filename={}", filename, e);
            throw new BizException(500, "文档解析失败: " + e.getClass().getSimpleName());
        }

        String text = normalizeWhitespace(handler.toString());
        Integer pageCount = extractPageCount(metadata);
        String contentType = metadata.get(org.apache.tika.metadata.HttpHeaders.CONTENT_TYPE);

        Parsed parsed = new Parsed(text, pageCount, contentType, text.length());
        log.info("[RAG] Tika 解析完成: filename={}, contentType={}, chars={}, pages={}",
                filename, contentType, parsed.charCount(), pageCount);
        return parsed;
    }

    /**
     * 整理正文里的多余空白：合并 3+ 连续换行为 2 段，trim 首尾。
     * <p>Tika 解析出的正文常带 "\n\n\n\n" 这类空白，对中文切片策略不友好。
     */
    private static String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text
                // 合并 3+ 连续换行为 2 段
                .replaceAll("\n{3,}", "\n\n")
                // 合并 3+ 连续空白为单空格
                .replaceAll("[ \\t]{3,}", "  ")
                .trim();
    }

    /**
     * 从 Tika Metadata 抽取页数。不同格式用不同的 key：
     * <ul>
     *   <li>PDF：{@code pdf:pageCount} 或 {@code xmpTPg:npages}</li>
     *   <li>DOCX/PPTX：通常无显式页数字段，{@code null} 由 caller 兜底</li>
     * </ul>
     */
    private static Integer extractPageCount(Metadata metadata) {
        String[] keys = {"pdf:pageCount", "xmpTPg:npages", "Page-Count"};
        for (String key : keys) {
            String value = metadata.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                // 格式不可解析时继续找下一个 key
            }
        }
        return null;
    }

    /**
     * Tika 解析后的统一结果。
     *
     * @param text        正文（已经过空白整理）
     * @param pageCount   文档页数（部分格式取不到，为 null）
     * @param contentType MIME（来自 Tika 嗅探）
     * @param charCount   正文长度（按 Java {@code String.length()} 计）
     */
    public record Parsed(String text, Integer pageCount, String contentType, int charCount) {
    }
}
