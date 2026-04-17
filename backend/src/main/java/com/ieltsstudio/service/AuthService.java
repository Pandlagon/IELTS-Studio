package com.ieltsstudio.service;

import com.ieltsstudio.dto.LoginRequest;
import com.ieltsstudio.dto.RegisterRequest;
import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.UserMapper;
import com.ieltsstudio.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 *
 * <p>处理用户注册、登录和个人信息查询，
 * 统一通过 {@link #buildAuthResult} 生成包含 Token 和用户信息的响应。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WordBookService wordBookService;

    /**
     * 用户登录
     *
     * <p>支持使用用户名或邮箱登录，密码使用 BCrypt 比对。
     *
     * @param req 登录请求（用户名/邮箱 + 密码）
     * @return 包含 token 和 user 信息的 Map
     * @throws RuntimeException 用户不存在或密码错误时抛出
     */
    public Map<String, Object> login(LoginRequest req) {
        // 先按用户名查，找不到再按邮箱查（支持两种登录方式）
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null) {
            user = userMapper.findByEmail(req.getUsername());
        }
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        return buildAuthResult(user);
    }

    /**
     * 用户注册
     *
     * <p>注册成功后自动创建默认词书，并直接返回登录态（免去二次登录）。
     *
     * @param req 注册请求（用户名 + 邮箱 + 密码）
     * @return 包含 token 和 user 信息的 Map
     * @throws RuntimeException 用户名或邮箱已被占用时抛出
     */
    public Map<String, Object> register(RegisterRequest req) {
        // 校验用户名和邮箱唯一性
        if (userMapper.findByUsername(req.getUsername()) != null) {
            throw new RuntimeException("用户名已被使用");
        }
        if (userMapper.findByEmail(req.getEmail()) != null) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 创建新用户，密码加密存储
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("USER");
        userMapper.insert(user);

        // 为新用户自动创建默认词书
        wordBookService.ensureDefaultBook(user.getId());

        return buildAuthResult(user);
    }

    /**
     * 获取当前用户个人信息
     *
     * @param userId 用户 ID（从 JWT Token 解析）
     * @return 用户信息 Map（不含密码）
     */
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("avatar", user.getAvatar());
        return userInfo;
    }

    /**
     * 构建认证响应（Token + 用户信息）
     *
     * <p>登录和注册共用此方法，避免重复代码。
     */
    private Map<String, Object> buildAuthResult(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("avatar", user.getAvatar());
        userInfo.put("role", user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", userInfo);
        return result;
    }
}
