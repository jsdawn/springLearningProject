package com.example.productservice.service;

import com.example.common.response.PageResult;
import com.example.productservice.dto.ProductPageQuery;
import com.example.productservice.entity.Product;

import java.util.List;

public interface ProductService {

    List<Product> listProducts();

    Product getProductById(Long id);

    Product createProduct(Product product);

    Product updateProduct(Product product);

    Product changeProductStatus(Long id, Integer status);

    Product adjustProductStock(Long id, Integer delta);

    /**
     * 商品分页查询（手写 LIMIT 分页，便于理解 MyBatis 分页实现）。
     */
    PageResult<Product> pageProducts(ProductPageQuery query);
}
