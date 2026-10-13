package com.labreserve.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>关于 {@code createdAt} / {@code updatedAt} 的自动填充：
 * 本项目<b>不</b>使用 {@code MetaObjectHandler}，而是依赖数据库列定义
 * （{@code DEFAULT CURRENT_TIMESTAMP} 与 {@code ON UPDATE CURRENT_TIMESTAMP}），
 * 这样无论从 Java 还是手工 SQL 写入都能保证时间字段正确。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件（MySQL 方言）
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，兜住前端传入过大的 pageSize
        pagination.setMaxLimit(500L);
        // 超过最大页时不回到第一页，直接返回空列表
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        // 防止误写全表 UPDATE / DELETE（没有 WHERE 条件时直接抛异常）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
