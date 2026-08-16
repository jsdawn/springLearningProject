package com.example.productservice.service.impl;

import com.example.common.response.PageResult;
import com.example.productservice.dto.ProductPageQuery;
import com.example.productservice.entity.Product;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public List<Product> listProducts() {
        return productMapper.findAll();
    }

    @Override
    public Product getProductById(Long id) {
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new IllegalArgumentException("Product not found, id=" + id);
        }
        return product;
    }

    @Override
    public Product createProduct(Product product) {
        int rows = productMapper.insert(product);
        if (rows <= 0 || product.getId() == null) {
            throw new IllegalStateException("Create product failed");
        }
        return productMapper.findById(product.getId());
    }

    @Override
    public Product updateProduct(Product product) {
        int rows = productMapper.updateById(product);
        if (rows <= 0) {
            throw new IllegalStateException("Update product failed, id=" + product.getId());
        }
        return productMapper.findById(product.getId());
    }

    @Override
    public Product changeProductStatus(Long id, Integer status) {
        int rows = productMapper.updateStatusById(id, status);
        if (rows <= 0) {
            throw new IllegalStateException("Change product status failed, id=" + id);
        }
        return getProductById(id);
    }

    @Override
    public Product adjustProductStock(Long id, Integer delta) {
        if (delta == null || delta == 0) {
            throw new IllegalArgumentException("delta must not be 0");
        }

        Product product = getProductById(id);
        int rows = productMapper.adjustStockById(id, delta);
        if (rows <= 0) {
            throw new IllegalStateException("Adjust product stock failed, id=" + id);
        }
        return getProductById(product.getId());
    }

    @Override
    public PageResult<Product> pageProducts(ProductPageQuery query) {
        int pageNum = query == null || query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query == null || query.getPageSize() == null ? 10 : query.getPageSize();
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }

        int offset = (pageNum - 1) * pageSize;
        String keyword = query == null ? null : query.getKeyword();

        long total = productMapper.countByKeyword(keyword);
        if (total <= 0) {
            return PageResult.empty(pageNum, pageSize);
        }

        return PageResult.of(productMapper.findPageByKeyword(keyword, offset, pageSize), total, pageNum, pageSize);
    }
}
