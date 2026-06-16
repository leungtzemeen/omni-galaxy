package com.omnigalaxy.platform.auth.util;

import com.omnigalaxy.platform.auth.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT 签发工具。
 * platform-auth 只负责生成 Token；Token 的解析与验证由网关和 common-security 负责。
 *
 * <p>Payload 设计：
 * <ul>
 *   <li>{@code sub}   — userId（String）</li>
 *   <li>{@code roles} — 平台级角色列表（如 ROLE_USER、ROLE_SUPER_ADMIN）</li>
 *   <li>{@code jti}   — 令牌唯一标识，登出时作为 Redis 黑名单 Key（auth:token:blacklist:{jti}）</li>
 *   <li>{@code iat}   — 签发时间</li>
 *   <li>{@code exp}   — 过期时间</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtProperties jwtProperties;

    public String generateToken(Long userId, List<String> roles) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpireMs());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signKey())
                .compact();
    }

    public long getExpireSeconds() {
        return jwtProperties.getExpireMs() / 1000;
    }

    private SecretKey signKey() {
        // 与 JwtClaimsResolver（common-security）保持一致，两端必须使用相同的 Decoder，
        // 否则签发密钥与校验密钥字节序列不同，签名校验必然失败。
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(jwtProperties.getSecret()));
    }
}
