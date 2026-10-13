package com.labreserve.common.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labreserve.common.constant.CommonConst;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页请求基类。
 *
 * <p>所有 {@code *_page} 接口的请求体都应继承本类，
 * 这样分页参数（pageNum / pageSize）的位置与校验规则在全项目保持一致。
 *
 * <p>注意：本项目所有参数都走 JSON body，因此分页参数也放在请求体里，
 * 不用 query string。
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    private Integer pageNum = CommonConst.DEFAULT_PAGE_NUM;

    /** 每页条数，上限 {@link CommonConst#PAGE_SIZE_MAX} */
    private Integer pageSize = CommonConst.DEFAULT_PAGE_SIZE;

    /** 兜底后的页码（<=0 或 null 时取 1） */
    public long safePageNum() {
        return (pageNum == null || pageNum < 1) ? CommonConst.DEFAULT_PAGE_NUM : pageNum;
    }

    /** 兜底并封顶后的每页条数 */
    public long safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return CommonConst.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, CommonConst.PAGE_SIZE_MAX);
    }

    /** 转成 MyBatis-Plus 分页对象 */
    public <T> Page<T> toMpPage() {
        return new Page<>(safePageNum(), safePageSize());
    }
}
