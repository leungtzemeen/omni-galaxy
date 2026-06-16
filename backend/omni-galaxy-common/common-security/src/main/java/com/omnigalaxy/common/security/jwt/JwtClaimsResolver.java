package com.omnigalaxy.common.security.jwt;

import com.omnigalaxy.common.security.config.JwtSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * JWT 解析与签名校验的唯一实现，供 platform-gateway（令牌校验过滤器）
 * 与 platform-auth-biz（/auth/logout 计算黑名单 TTL）共用，避免两端
 * 各自实现一套校验逻辑导致密钥读取方式或异常处理口径不一致。
 *
 * <p>本类只负责"解析 + 签名校验"，不做黑名单 / 熔断时间戳判定——
 * 那是调用方（网关过滤器）结合 Redis 状态做的业务判定。
 *
 * <p>签名失败、过期、格式错误时直接抛出 {@link JwtException}（及其子类
 * {@code ExpiredJwtException} / {@code SignatureException} / {@code MalformedJwtException}），
 * 由调用方统一捕获并转译为 401。
 */
@RequiredArgsConstructor
public class JwtClaimsResolver {

    private final JwtSecurityProperties properties;

    public ResolvedToken resolve(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long userId = Long.valueOf(claims.getSubject());
        List<String> roles = claims.get("roles", List.class);

        return new ResolvedToken(
                userId,
                roles == null ? Set.of() : Set.copyOf(roles),
                claims.getId(),
                claims.getIssuedAt(),
                claims.getExpiration()
        );
    }

    private SecretKey signKey() {
        // JWT 规范（RFC 7519）使用 Base64URL 编码；标准 Base64 不认识 '-' 和 '_'，
        // 而 HMAC-SHA256 签名的 Base64URL 结果必然含这两个字符，因此必须用 BASE64URL。
        return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(properties.getSecret()));
    }

    /**
     * 解析后的 JWT 关键信息快照。
     *
     * @param jti        JWT ID，用于单 token 黑名单精确匹配；旧版本（未升级前签发）的 token 此字段为 null
     * @param issuedAt   签发时间（{@code iat}），与 {@code auth:reset:{userId}} 比较时统一按 epoch millis
     * @param expiration 过期时间（{@code exp}），用于计算黑名单写入 Redis 时的剩余 TTL
     */
    public record ResolvedToken(Long userId, Set<String> roles, String jti, Date issuedAt, Date expiration) {
    }
}