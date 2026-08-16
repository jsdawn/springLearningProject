package com.example.productservice.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 商品分页查询入参（GET QueryString）。
 *
 * pageNum/pageSize 为统一分页入参；keyword 用于 productName/remark 模糊匹配，可按需扩展更多条件字段。
 */
public class ProductPageQuery {

    /**
     * 页码，默认 1
     */
    @Min(value = 1, message = "页码最小为 1")
    private Integer pageNum = 1;

    /**
     * 每页条数，默认 10
     */
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private Integer pageSize = 10;

    /**
     * 模糊搜索关键字（productName/remark）
     */
    private String keyword;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
