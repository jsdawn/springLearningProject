package com.example.orderservice.feign;

import com.example.common.response.ApiResponse;
import com.example.orderservice.client.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserFeignClient {

    @GetMapping("/users/internal/{id}")
    ApiResponse<UserSummary> getUserById(@PathVariable("id") Long id);
}
