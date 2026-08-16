package com.example.productservice.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
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

@RestController
@RequestMapping("/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

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

    @PostMapping
    public ApiResponse<Product> create(@Valid @RequestBody Product product) {
        product.setId(null);
        return ApiResponse.success("Product created successfully", productService.createProduct(product));
    }

    @PutMapping("/{id}")
    public ApiResponse<Product> update(@PathVariable("id") Long id, @Valid @RequestBody Product product) {
        product.setId(id);
        return ApiResponse.success("Product updated successfully", productService.updateProduct(product));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Product> changeStatus(@PathVariable("id") Long id,
                                             @RequestParam("status") Integer status) {
        return ApiResponse.success("Product status updated successfully", productService.changeProductStatus(id, status));
    }

    @PostMapping("/{id}/stock")
    public ApiResponse<Product> adjustStock(@PathVariable("id") Long id,
                                            @RequestParam("delta") Integer delta) {
        return ApiResponse.success("Product stock updated successfully", productService.adjustProductStock(id, delta));
    }
}
