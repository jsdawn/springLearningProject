package com.example.userservice.controller;

import com.example.common.response.ApiResponse;
import com.example.common.response.PageResult;
import com.example.common.role.RequirePermission;
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

/**
 * 用户管理接口（管理台专属，全部需要对应权限点）。
 *
 * <p>RBAC 校验方式：@RequirePermission("users:xxx") 注解 + RoleCheckAspect 切面，
 * 权限点编码与 sys_permission 表 perm_code 一一对应；登录时角色/权限装配进 JWT，
 * 网关转 X-Auth-Roles / X-Auth-Perms 透传下游。</p>
 */
@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequirePermission("users:list")
    @GetMapping
    public ApiResponse<List<User>> list() {
        return ApiResponse.success(userService.listUsers());
    }

    @RequirePermission("users:page")
    @GetMapping("/page")
    public ApiResponse<PageResult<User>> page(@Valid UserPageQuery query) {
        return ApiResponse.success(userService.pageUsers(query));
    }

    @RequirePermission("users:detail")
    @GetMapping("/{id}")
    public ApiResponse<User> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    /**
     * 内部用户查询接口：仅供 order-service 经 Feign（服务发现直连，不经网关）在下单时校验用户，
     * 无用户角色上下文，故不设权限门槛。生产上应叠加内网隔离 / 内部调用凭证。
     */
    @GetMapping("/internal/{id}")
    public ApiResponse<User> detailInternal(@PathVariable("id") Long id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @RequirePermission("users:create")
    @PostMapping
    public ApiResponse<User> create(@Valid @RequestBody User user) {
        user.setId(null);
        return ApiResponse.success("User created successfully", userService.createUser(user));
    }

    @RequirePermission("users:update")
    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable("id") Long id, @Valid @RequestBody User user) {
        user.setId(id);
        return ApiResponse.success("User updated successfully", userService.updateUser(user));
    }

    @RequirePermission("users:status")
    @PatchMapping("/{id}/status")
    public ApiResponse<User> changeStatus(@PathVariable("id") Long id,
                                          @RequestParam("status") Integer status) {
        return ApiResponse.success("User status updated successfully", userService.changeUserStatus(id, status));
    }
}
