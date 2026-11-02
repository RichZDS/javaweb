package com.richzds.quiz.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.config.AppProperties;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.mapper.UserMapper;

/** 启动时如果库里没有管理员，就按 app.admin.username / password 建一个。 */
@Component
@Order(10)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final Clock clock;

    public AdminBootstrap(UserMapper userMapper, PasswordEncoder passwordEncoder, AppProperties props, Clock clock) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdmin();
    }

    public void ensureAdmin() {
        long admins = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, UserService.ROLE_ADMIN));
        if (admins > 0) {
            return;
        }
        String username = props.admin().username();
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            log.error("没有管理员账号，但用户名 {} 已被普通用户占用；请换一个 ADMIN_USERNAME", username);
            return;
        }
        LocalDateTime now = TimeUtil.nowUtc(clock);
        userMapper.insert(new User()
                .setUsername(username)
                .setPasswordHash(passwordEncoder.encode(props.admin().password()))
                .setNickname("管理员")
                .setRole(UserService.ROLE_ADMIN)
                .setStatus(UserService.STATUS_ACTIVE)
                .setCreatedAt(now)
                .setUpdatedAt(now));
        log.info("已创建管理员账号 {}", username);
    }
}
