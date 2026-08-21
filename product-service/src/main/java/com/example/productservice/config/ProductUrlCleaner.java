package com.example.productservice.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.UrlCleaner;
import org.springframework.stereotype.Component;

/**
 * Sentinel 资源名归并处理器。
 *
 * <p>默认情况下，含路径变量的请求会按具体 URI 统计资源，
 * 例如 /products/1、/products/2 会各自成为独立资源，导致资源碎片化、规则无法统一配置。
 * 这里将路径变量统一归并为 /products/{id}，使商品详情类接口共享同一个资源名，
 * 便于在 Nacos 中针对 GET:/products/{id} 配置限流与熔断降级规则。</p>
 *
 * <p>该 Bean 由 spring-cloud-alibaba-sentinel 自动装配识别并注入，无需额外注册。</p>
 */
@Component
public class ProductUrlCleaner implements UrlCleaner {

    /** 路径中纯数字段归并模板：/products/123 -> /products/{id} */
    private static final String PATH_VARIABLE_PLACEHOLDER = "/{id}";

    @Override
    public String clean(String originUrl) {
        if (originUrl == null || originUrl.isEmpty()) {
            return originUrl;
        }
        // 将路径中的纯数字段替换为 {id} 占位符，例如：
        // /products/123 -> /products/{id}
        // /products/123/stock -> /products/{id}/stock
        return originUrl.replaceAll("/\\d+", PATH_VARIABLE_PLACEHOLDER);
    }
}
