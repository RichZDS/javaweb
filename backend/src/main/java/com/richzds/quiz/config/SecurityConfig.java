package com.richzds.quiz.config;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.security.JsonErrorWriter;
import com.richzds.quiz.security.JwtAuthFilter;
import com.richzds.quiz.security.JwtService;

/** 无状态 JWT 鉴权：匿名可访问注册、登录、App 版本、Swagger 和上传文件；/api/admin/** 只给 ADMIN。 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, UserMapper userMapper,
                                                   JsonErrorWriter errorWriter, Clock clock,
                                                   AppProperties props) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(c -> c.configurationSource(corsSource(props.cors().allowedOriginPatterns())))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/app/version").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(errorWriter).accessDeniedHandler(errorWriter))
                .addFilterBefore(new JwtAuthFilter(jwtService, userMapper, errorWriter, clock),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 只有配置了 app.cors.allowed-origin-patterns（dev 下是 http://localhost:*）才放行跨域。 */
    private static CorsConfigurationSource corsSource(List<String> patterns) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (patterns != null && !patterns.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOriginPatterns(patterns);
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("*"));
            cfg.setMaxAge(3600L);
            source.registerCorsConfiguration("/**", cfg);
        }
        return source;
    }
}
