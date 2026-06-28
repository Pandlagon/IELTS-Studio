package com.ieltsstudio.config;

import com.ieltsstudio.entity.User;
import com.ieltsstudio.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 管理员账号启动时自动初始化（Phase 8A 配套）。
 *
 * <p>读取配置 {@code app.init-admin.username / email / password}
 * （对应环境变量 {@code INIT_ADMIN_USERNAME / INIT_ADMIN_EMAIL / INIT_ADMIN_PASSWORD}），
 * 在应用启动时<strong>幂等</strong>地创建或升级一个内置 ADMIN 账号。
 *
 * <h3>行为规则</h3>
 * <ul>
 *   <li>三项配置任意一项为空 → 跳过初始化（不影响现有行为）。</li>
 *   <li>库中已存在任意 ADMIN（<strong>含已禁用 deleted=1</strong>）→ 跳过初始化，
 *       避免重启重复创建、避免环境变量初始密码被重新激活。</li>
 *   <li>目标 username/email <strong>未被占用</strong> → 创建新 ADMIN 用户。</li>
 *   <li>目标 username/email <strong>已被占用</strong>（且库中无 ADMIN）→
 *       将该现有 USER 升级为 ADMIN 并重置密码；若该账号被禁用则一并启用。
 *       <br>（幂等：升级后该用户成为 ADMIN，下次启动因已有 ADMIN 跳过，密码不会被再次重置）</li>
 *   <li>密码使用 {@link PasswordEncoder#encode(CharSequence)} 存储 BCrypt 哈希。</li>
 *   <li>日志只记录 username，<strong>不记录明文密码</strong>。</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <ul>
 *   <li><b>本地</b>：在根目录 {@code .env} 填入 {@code INIT_ADMIN_USERNAME/EMAIL/PASSWORD}，
 *       首次启动后即创建/升级，之后可保留或删除该配置（不影响）。</li>
 *   <li><b>生产</b>：通过环境变量注入上述三项，部署后首次启动即创建管理员。</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminBootstrapConfig {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner adminBootstrapRunner(
            @Value("${app.init-admin.username:}") String username,
            @Value("${app.init-admin.email:}") String email,
            @Value("${app.init-admin.password:}") String password) {
        return args -> initAdminIfNeeded(username, email, password);
    }

    /**
     * 幂等初始化管理员账号。
     *
     * <p>包级可见以方便单元测试（不强制）。</p>
     */
    void initAdminIfNeeded(String username, String email, String password) {
        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            log.info("未配置 app.init-admin.*，跳过管理员初始化");
            return;
        }

        // 幂等：库中已存在任意 ADMIN（含禁用）则跳过，避免初始密码被重新激活
        Long existingAdmins = userMapper.countAllAdminsIncludingDeleted();
        if (existingAdmins != null && existingAdmins > 0) {
            log.info("已存在 {} 个 ADMIN 用户（含禁用），跳过管理员初始化", existingAdmins);
            return;
        }

        String trimmedUsername = username.trim();
        String trimmedEmail = email.trim();

        // 查目标 username/email 是否已有对应账号（含禁用）
        User existing = userMapper.selectByUsernameOrEmailIncludingDeleted(trimmedUsername, trimmedEmail);
        if (existing != null) {
            // 已有同用户名/邮箱的账号（当前必为非 ADMIN，因上面已确认库中无 ADMIN）
            // → 升级为 ADMIN + 重置密码；若被禁用则一并启用
            Long uid = existing.getId();
            userMapper.updateRoleById(uid, "ADMIN");
            userMapper.updatePasswordById(uid, passwordEncoder.encode(password));
            if (existing.getDeleted() != null && existing.getDeleted() != 0) {
                userMapper.updateDeletedById(uid, 0);
            }
            log.info("已将现有用户升级为管理员并重置密码：username={}", trimmedUsername);
            return;
        }

        // 不存在 → 创建新 ADMIN
        User user = new User();
        user.setUsername(trimmedUsername);
        user.setEmail(trimmedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("ADMIN");
        user.setDeleted(0);
        userMapper.insert(user);
        // 不记录密码，只记录用户名
        log.info("管理员账号初始化完成：username={}", user.getUsername());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
