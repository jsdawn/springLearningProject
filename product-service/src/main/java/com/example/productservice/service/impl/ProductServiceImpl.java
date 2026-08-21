package com.example.productservice.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.example.common.response.PageResult;
import com.example.common.utils.RedisUtil;
import com.example.productservice.dto.ProductPageQuery;
import com.example.productservice.entity.Product;
import com.example.productservice.mapper.ProductMapper;
import com.example.productservice.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    /** 缓存Key统一规范：product:info:{productId} */
    private static final String CACHE_KEY_PREFIX = "product:info:";

    /** 缓存基础过期时间：30分钟（Redis断电有丢失风险，仅缓存热点数据，不可替代MySQL持久存储） */
    private static final long CACHE_EXPIRE_MINUTES = 30;

    /** 缓存过期时间随机抖动上限：0~5分钟，打散过期时间点，防止缓存雪崩 */
    private static final long CACHE_TTL_JITTER_MINUTES = 5;

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

    /**
     * 商品详情查询，集成 Sentinel 熔断降级。
     * <p>
     * 当方法抛出异常（如 DB 超时）且异常类型不在 exceptionsToIgnore 中时，
     * 触发 fallback 方法返回降级数据（尝试读缓存或返回默认对象）。
     * 业务异常（IllegalArgumentException，如商品不存在）被忽略，直接抛出由 GlobalExceptionHandler 处理。
     */
    @Override
    @SentinelResource(
            value = "getProductById",
            fallback = "getProductByIdFallback",
            exceptionsToIgnore = {IllegalArgumentException.class}
    )
    public Product getProductById(Long id) {
        // 缓存三防（穿透/击穿/雪崩）统一封装在 RedisUtil#getOrLoad：
        // 未命中加互斥锁回源、DB不存在缓存空值标记、真值写入附加随机抖动TTL
        Product product = redisUtil.getOrLoad(CACHE_KEY_PREFIX + id,
                CACHE_EXPIRE_MINUTES, CACHE_TTL_JITTER_MINUTES, TimeUnit.MINUTES,
                () -> productMapper.findById(id));
        if (product == null) {
            throw new IllegalArgumentException("Product not found, id=" + id);
        }
        return product;
    }

    /**
     * getProductById 的降级方法。
     * 当 DB 查询失败或超时时，尝试从 Redis 直接获取缓存数据；
     * 若缓存也无，则抛出异常由 GlobalExceptionHandler 统一返回 503 业务错误码。
     */
    @SuppressWarnings("unchecked")
    public Product getProductByIdFallback(Long id, Throwable ex) {
        log.warn("商品详情服务降级，尝试从缓存获取, id={}", id, ex);
        // 尝试直接从 Redis 获取缓存（绕过 getOrLoad 的 DB 回源逻辑）
        Object cached = redisUtil.get(CACHE_KEY_PREFIX + id);
        if (cached instanceof Product) {
            return (Product) cached;
        }
        // 缓存也没有，抛出异常走全局异常处理，返回 {code:503, message:"服务降级..."}
        throw new IllegalStateException("服务降级，商品详情暂时无法获取, id=" + id);
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
