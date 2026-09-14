package com.example.productservice.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.common.role.RequirePermission;
import com.example.productservice.dto.ProductPageQuery;
import com.example.productservice.entity.Product;
import com.example.productservice.service.ProductService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 商品接口：查询（list/page/detail）保持开放，供商城展示与下单链路 Feign 调 detail；
 * 管理台写接口（create/update/status/stock）需要对应权限点。
 */
@RestController
@RequestMapping("/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ---- 查询接口：开放（商城展示 + 下单链路 Feign 调 detail，不设权限门槛）----

    @GetMapping
    public ApiResponse<List<Product>> list() {
        return ApiResponse.success(productService.listProducts());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Product>> page(@Valid ProductPageQuery query) {
        return ApiResponse.success(productService.pageProducts(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(productService.getProductById(id));
    }

    // ---- 管理台写接口：需要对应权限点 ----

    @RequirePermission("products:create")
    @PostMapping
    public ApiResponse<Product> create(@Valid @RequestBody Product product) {
        product.setId(null);
        return ApiResponse.success("Product created successfully", productService.createProduct(product));
    }

    @RequirePermission("products:update")
    @PutMapping("/{id}")
    public ApiResponse<Product> update(@PathVariable("id") Long id, @Valid @RequestBody Product product) {
        product.setId(id);
        return ApiResponse.success("Product updated successfully", productService.updateProduct(product));
    }

    @RequirePermission("products:status")
    @PatchMapping("/{id}/status")
    public ApiResponse<Product> changeStatus(@PathVariable("id") Long id,
                                             @RequestParam("status") Integer status) {
        return ApiResponse.success("Product status updated successfully", productService.changeProductStatus(id, status));
    }

    /**
     * 管理台调整库存（补货/扣减）：需要 products:stock 权限点。
     */
    @RequirePermission("products:stock")
    @PostMapping("/{id}/stock")
    public ApiResponse<Product> adjustStock(@PathVariable("id") Long id,
                                            @RequestParam("delta") Integer delta) {
        return ApiResponse.success("Product stock updated successfully", productService.adjustProductStock(id, delta));
    }

    /**
     * 内部扣库存接口：仅供 order-service 经 Feign（服务发现直连，不经网关）调用，
     * 无用户角色上下文，故不设权限门槛。生产上应叠加内网隔离 / 内部调用凭证。
     */
    @PostMapping("/internal/{id}/stock")
    public ApiResponse<Product> adjustStockInternal(@PathVariable("id") Long id,
                                                    @RequestParam("delta") Integer delta) {
        return ApiResponse.success("Product stock updated successfully", productService.adjustProductStock(id, delta));
    }
}
