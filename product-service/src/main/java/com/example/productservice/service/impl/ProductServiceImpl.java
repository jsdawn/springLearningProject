package com.example.productservice.service.impl;

import com.example.common.response.PageResult;
import com.example.common.utils.RedisUtil;
import com.example.productservice.dto.ProductPageQuery;
import com.example.productservice.entity.Product;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.service.ProductService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductService {

    /** 缓存Key统一规范：product:info:{productId} */
    private static final String CACHE_KEY_PREFIX = "product:info:";

    /** 缓存过期时间：30分钟（Redis断电有丢失风险，仅缓存热点数据，不可替代MySQL持久存储） */
    private static final long CACHE_EXPIRE_MINUTES = 30;

    private final ProductMapper productMapper;
    private final RedisUtil redisUtil;

    public ProductServiceImpl(ProductMapper productMapper, RedisUtil redisUtil) {
        this.productMapper = productMapper;
        this.redisUtil = redisUtil;
    }

    @Override
    public List<Product> listProducts() {
        return productMapper.findAll();
    }

    @Override
    public Product getProductById(Long id) {
        String key = CACHE_KEY_PREFIX + id;

        // ① 先查询Redis缓存，命中直接返回
        Object cached = redisUtil.get(key);
        if (cached != null) {
            return (Product) cached;
        }

        // ② 缓存未命中，查询MySQL
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new IllegalArgumentException("Product not found, id=" + id);
        }

        // ③ 查询成功写入Redis缓存，设置过期时间
        redisUtil.set(key, product, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);

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

        // 更新数据库成功后，删除对应商品缓存，保证数据一致性（Cache Aside Pattern）
        redisUtil.delete(CACHE_KEY_PREFIX + product.getId());

        // 返回最新数据（下次查询时重新回写缓存）
        return productMapper.findById(product.getId());
    }

    @Override
    public Product changeProductStatus(Long id, Integer status) {
        int rows = productMapper.updateStatusById(id, status);
        if (rows <= 0) {
            throw new IllegalStateException("Change product status failed, id=" + id);
        }

        // 状态更新后删除缓存，避免后续 getProductById 返回旧缓存数据
        redisUtil.delete(CACHE_KEY_PREFIX + id);

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

        // 库存调整后删除缓存，避免后续 getProductById 返回旧缓存数据
        redisUtil.delete(CACHE_KEY_PREFIX + id);

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
