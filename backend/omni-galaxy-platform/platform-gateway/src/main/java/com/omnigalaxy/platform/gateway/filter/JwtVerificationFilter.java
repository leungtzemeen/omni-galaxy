package com.omnigalaxy.platform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import com.omnigalaxy.common.security.jwt.JwtClaimsResolver;
import com.omnigalaxy.platform.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;

/**
 * 网关令牌校验过滤器：统一收口 JWT 解析 + 登出黑名单 + 全局熔断校验，
 * 通过后将 userId/roles 安全注入 X-User-Id / X-User-Roles，下游微服务的
 * SecurityInterceptor 据此可以安全、清白地信任这两个 Header。
 *
 * <p>判定链路（命中任一即拒绝，401）：
 * <ol>
 *   <li>缺少 Authorization Bearer 或签名/格式非法/已过期</li>
 *   <li>{@code auth:token:blacklist:{jti}} 存在 —— 该 Token 已主动登出</li>
 *   <li>{@code iat} 早于 {@code auth:reset:{userId}} —— 该 Token 签发于全局熔断（改密/风控）之前</li>
 * </ol>
 *
 * <p>已知技术债：{@link GatewaySecurityProperties#getPublicPaths()} 白名单需随各业务域
 * 新增公开接口手动维护，本期不做声明式治理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVerificationFilter implements GlobalFilter, Ordered {

    private static final String BLACKLIST_PREFIX = "auth:token:blacklist:";
    private static final String RESET_PREFIX     = "auth:reset:";
    private static final String HEADER_USER_ID    = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final JwtClaimsResolver jwtClaimsResolver;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public int getOrder() {
        // 必须早于路由转发过滤器执行，未通过校验的请求不应消耗任何下游资源
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (token == null) {
            log.warn(">>>> [Gateway] 请求未携带 Authorization Bearer，已拦截（处理策略：401）path: {}", path);
            return unauthorized(exchange);
        }

        JwtClaimsResolver.ResolvedToken resolved;
        try {
            resolved = jwtClaimsResolver.resolve(token);
        } catch (JwtException e) {
            log.warn(">>>> [Gateway] JWT 校验失败，已拦截（处理策略：401）path: {} reason: {}", path, e.getMessage());
            return unauthorized(exchange);
        }

        return checkRevocation(resolved)
                .flatMap(revoked -> {
                    if (revoked) {
                        log.warn(">>>> [Gateway] Token 已登出或已被全局熔断，已拦截（处理策略：401）path: {} userId: {}",
                                path, resolved.userId());
                        return unauthorized(exchange);
                    }
                    return chain.filter(exchange.mutate().request(injectUserHeaders(request, resolved)).build());
                });
    }

    /**
     * 一次性并行读取黑名单 / 全局熔断时间戳两个 key，命中任一即视为已撤销。
     * 两个时间戳统一按 epoch millis 比较，与 platform-auth-biz 写入时的单位保持一致。
     */
    private Mono<Boolean> checkRevocation(JwtClaimsResolver.ResolvedToken resolved) {
        Mono<Boolean> blacklisted = resolved.jti() == null
                ? Mono.just(false)
                : redisTemplate.hasKey(BLACKLIST_PREFIX + resolved.jti());

        Mono<Boolean> resetBefore = redisTemplate.opsForValue()
                .get(RESET_PREFIX + resolved.userId())
                .map(resetMillisStr -> {
                    long resetMillis = Long.parseLong(resetMillisStr);
                    Date issuedAt = resolved.issuedAt();
                    return issuedAt != null && issuedAt.getTime() < resetMillis;
                })
                .defaultIfEmpty(false);

        return Mono.zip(blacklisted, resetBefore, (b, r) -> b || r);
    }

    private ServerHttpRequest injectUserHeaders(ServerHttpRequest request, JwtClaimsResolver.ResolvedToken resolved) {
        return request.mutate()
                .headers(headers -> {
                    headers.set(HEADER_USER_ID, String.valueOf(resolved.userId()));
                    headers.set(HEADER_USER_ROLES, String.join(",", resolved.roles()));
                })
                .build();
    }

    private boolean isPublicPath(String path) {
        return securityProperties.getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.failed(ResultCodeEnum.UNAUTHORIZED.getCode(), ResultCodeEnum.UNAUTHORIZED.getMsg()));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (Exception e) {
            log.error(">>>> [Gateway] 401 响应体序列化失败", e);
            return response.setComplete();
        }
    }
}