package com.example.productservice.mapper;

import com.example.productservice.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> findAll();

    Product findById(Long id);

    int insert(Product product);

    int updateById(Product product);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);

    int adjustStockById(@Param("id") Long id, @Param("delta") Integer delta);

    /**
     * 分页查询（带可选 keyword 模糊搜索）。
     */
    List<Product> findPageByKeyword(@Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    /**
     * 统计总数（与分页查询使用同一组过滤条件）。
     */
    long countByKeyword(@Param("keyword") String keyword);
}
