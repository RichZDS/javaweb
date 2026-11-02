package com.richzds.quiz.security;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.richzds.quiz.common.ErrorCode;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.mapper.UserMapper;

/**
 * 解析 Bearer 令牌并按库里的最新状态建立登录身份：每次请求都查一次用户，
 * 所以禁用和改角色立刻生效。禁用用户直接返回 403。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    /** last_active_at 最多每 5 分钟写一次，避免每个请求都写库。 */
    private static final long ACTIVE_WRITE_INTERVAL_MIN = 5;

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final JsonErrorWriter errorWriter;
    private final Clock clock;

    public JwtAuthFilter(JwtService jwtService, UserMapper userMapper, JsonErrorWriter errorWriter, Clock clock) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.errorWriter = errorWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Long userId = jwtService.parseUserId(header.substring(7).trim());
            User user = userId == null ? null : userMapper.selectById(userId);
            if (user != null) {
                if (!"ACTIVE".equals(user.getStatus())) {
                    errorWriter.write(response, ErrorCode.FORBIDDEN, "账号已被禁用");
                    return;
                }
                CurrentUser principal = new CurrentUser(user.getId(), user.getUsername(), user.getRole());
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(auth);
                touchLastActive(user);
            }
            // 令牌无效时不建立身份：需要登录的接口由入口点返回 401，匿名接口照常放行
        }
        chain.doFilter(request, response);
    }

    private void touchLastActive(User user) {
        LocalDateTime now = TimeUtil.nowUtc(clock);
        LocalDateTime last = user.getLastActiveAt();
        if (last == null || last.plusMinutes(ACTIVE_WRITE_INTERVAL_MIN).isBefore(now)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .set(User::getLastActiveAt, now));
        }
    }
}
