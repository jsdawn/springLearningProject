package com.example.userservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RBAC 查询：登录时按用户名装配角色集合与权限点集合。
 * 链路：users → sys_user_role → sys_role（status=1）
 *       → sys_role_permission → sys_permission（status=1）
 */
@Mapper
public interface RolePermissionMapper {

    /**
     * 查询用户的角色编码集合（如 ADMIN、USER），只取启用状态的角色。
     */
    List<String> findRoleCodesByUsername(@Param("username") String username);

    /**
     * 查询用户经角色聚合后的权限点编码集合（如 users:list、products:create），
     * 只统计启用状态的角色与权限点，DISTINCT 去重。
     */
    List<String> findPermCodesByUsername(@Param("username") String username);
}
