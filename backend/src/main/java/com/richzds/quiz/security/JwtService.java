package com.richzds.quiz.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import com.richzds.quiz.config.AppProperties;

/** 签发和校验 JWT（HS256，默认 7 天有效）。过期判断用注入的 Clock，测试可调。 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(AppProperties props, Clock clock) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofDays(props.jwt().expireDays());
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public IssuedToken issue(long userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, Instant.ofEpochMilli(expiresAt.toEpochMilli()));
    }

    /** 返回令牌里的用户 id；签名不对、格式不对或已过期时返回 null。 */
    public Long parseUserId(String token) {
        try {
            String subject = parser.parseSignedClaims(token).getPayload().getSubject();
            return subject == null ? null : Long.valueOf(subject);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
