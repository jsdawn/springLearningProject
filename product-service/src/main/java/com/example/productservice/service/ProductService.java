package com.example.productservice.service;

import com.example.productservice.entity.Product;

import java.util.List;

public interface ProductService {

    List<Product> listProducts();

    Product getProductById(Long id);

    Product createProduct(Product product);

    Product updateProduct(Product product);

    Product changeProductStatus(Long id, Integer status);
}
