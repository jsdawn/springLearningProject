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
}
