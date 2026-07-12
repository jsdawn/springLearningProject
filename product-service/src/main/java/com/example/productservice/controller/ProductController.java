package com.example.productservice.controller;

import com.example.common.response.ApiResponse;
import com.example.productservice.entity.Product;
import com.example.productservice.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ApiResponse<List<Product>> list() {
        return ApiResponse.success(productService.listProducts());
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable Long id) {
        return ApiResponse.success(productService.getProductById(id));
    }

    @PostMapping
    public ApiResponse<Product> create(@RequestBody Product product) {
        product.setId(null);
        return ApiResponse.success("Product created successfully", productService.createProduct(product));
    }

    @PutMapping("/{id}")
    public ApiResponse<Product> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        return ApiResponse.success("Product updated successfully", productService.updateProduct(product));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Product> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        return ApiResponse.success("Product status updated successfully", productService.changeProductStatus(id, status));
    }

    @PatchMapping("/{id}/stock")
    public ApiResponse<Product> adjustStock(@PathVariable Long id, @RequestParam Integer delta) {
        return ApiResponse.success("Product stock updated successfully", productService.adjustProductStock(id, delta));
    }
}
