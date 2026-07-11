package com.example.orderservice.mapper;

import com.example.orderservice.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderMapper {

    List<OrderInfo> findAll();
}
