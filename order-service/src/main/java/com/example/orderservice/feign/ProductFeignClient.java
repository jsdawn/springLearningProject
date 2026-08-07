package com.example.orderservice.feign;

import com.example.common.response.ApiResponse;
import com.example.orderservice.client.ProductSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductFeignClient {

    @GetMapping("/products/{id}")
    ApiResponse<ProductSummary> getProductById(@PathVariable("id") Long id);

    @PostMapping("/products/{id}/stock")
    ApiResponse<ProductSummary> adjustStock(@PathVariable("id") Long id,
                                             @RequestParam("delta") Integer delta);
}
