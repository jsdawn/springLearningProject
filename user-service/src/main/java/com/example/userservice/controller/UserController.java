package com.example.userservice.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.userservice.dto.UserPageQuery;
import com.example.userservice.entity.User;
import com.example.userservice.service.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<User>> list() {
        return ApiResponse.success(userService.listUsers());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<User>> page(@Valid UserPageQuery query) {
        return ApiResponse.success(userService.pageUsers(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<User> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @PostMapping
    public ApiResponse<User> create(@Valid @RequestBody User user) {
        user.setId(null);
        return ApiResponse.success("User created successfully", userService.createUser(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable("id") Long id, @Valid @RequestBody User user) {
        user.setId(id);
        return ApiResponse.success("User updated successfully", userService.updateUser(user));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<User> changeStatus(@PathVariable("id") Long id,
                                          @RequestParam("status") Integer status) {
        return ApiResponse.success("User status updated successfully", userService.changeUserStatus(id, status));
    }
}
