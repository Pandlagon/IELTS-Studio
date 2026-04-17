package com.ieltsstudio.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ieltsstudio.common.Result;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.UserMapper;
import com.ieltsstudio.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户信息接口控制器
 *
 * <p>接口路径前缀：{@code /users}，所有接口均需登录认证。
 * 提供当前用户的信息查询、用户名修改和密码修改功能。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * GET /users/me — 获取当前用户完整信息
     * <p>返回结果不含密码字段。
     */
    @GetMapping("/me")
    public Result<?> getMe(@AuthenticationPrincipal AuthUser authUser) {
        User user = userMapper.selectById(authUser.getId());
        if (user == null) return Result.notFound("用户不存在");
        user.setPassword(null); // 脱敏：不返回密码
        return Result.success(user);
    }

    /**
     * PUT /users/me — 修改当前用户的用户名
     *
     * <p>请求体：{@code { "username": "新用户名" }}
     * <p>修改前校验用户名是否已被其他用户占用。
     */
    @PutMapping("/me")
    public Result<?> updateMe(@AuthenticationPrincipal AuthUser authUser,
                               @RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username != null && !username.isBlank()) {
            // 检查新用户名是否已被其他人使用
            User existing = userMapper.findByUsername(username);
            if (existing != null && !existing.getId().equals(authUser.getId())) {
                return Result.error(400, "用户名已被占用");
            }
            UpdateWrapper<User> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", authUser.getId()).set("username", username.trim());
            userMapper.update(null, wrapper);
        }
        return Result.success("更新成功");
    }

    /**
     * PUT /users/me/password — 修改密码
     *
     * <p>请求体：{@code { "oldPassword": "旧密码", "newPassword": "新密码（至少8位）" }}
     * <p>需要先验证旧密码正确后，才允许设置新密码。
     */
    @PutMapping("/me/password")
    public Result<?> changePassword(@AuthenticationPrincipal AuthUser authUser,
                                     @RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");

        // 参数基本校验
        if (oldPwd == null || newPwd == null || newPwd.length() < 8) {
            return Result.error(400, "参数无效，新密码至少8位");
        }

        // 验证旧密码
        User user = userMapper.selectById(authUser.getId());
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) {
            return Result.error(400, "原密码错误");
        }

        // 更新为新密码（BCrypt 加密）
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", authUser.getId()).set("password", passwordEncoder.encode(newPwd));
        userMapper.update(null, wrapper);

        return Result.success("密码修改成功");
    }
}
