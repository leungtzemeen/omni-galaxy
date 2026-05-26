package com.omnigalaxy.common.security.interceptor;

import com.omnigalaxy.common.core.context.LoginUser;
import com.omnigalaxy.common.core.context.UserContext;
import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import com.omnigalaxy.common.security.annotation.Logical;
import com.omnigalaxy.common.security.annotation.RequiresLogin;
import com.omnigalaxy.common.security.annotation.RequiresRoles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 核心安全拦截器，承担两项原子职责：
 *
 * <ol>
 *   <li><b>身份捕获</b>：从网关透传的 X-User-Id 与 X-User-Roles Header 中解析并构建 {@link LoginUser}，
 *       绑定至 {@link UserContext}，打通"网关 → 拦截器 → 持久层自动填充"黄金闭环。</li>
 *   <li><b>注解鉴权</b>：读取目标方法（优先）或类（兜底）上的 {@link RequiresLogin}/{@link RequiresRoles} 注解，
 *       执行硬线防御，不满足条件即抛出业务异常终止链路。</li>
 * </ol>
 *
 * <p>注意：本类不受 Spring 容器管理（new 实例化），因此不支持字段注入；所有依赖均为静态工具调用。
 */
@Slf4j
public class SecurityInterceptor implements HandlerInterceptor {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        log.info(">>>> [common-security] 安全拦截器入站 context: {} {}", request.getMethod(), request.getRequestURI());

        bindUserContext(request);
        enforceLogin(method);
        enforceRoles(method);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
        log.debug("<<<< [common-security] 请求结束，线程用户上下文已清理 content: {}", request.getRequestURI());
    }

    // -------------------------------------------------------------------------
    // 私有核心逻辑
    // -------------------------------------------------------------------------

    private void bindUserContext(HttpServletRequest request) {
        String userIdStr = request.getHeader(HEADER_USER_ID);
        if (!StringUtils.hasText(userIdStr)) {
            return;
        }
        try {
            Long userId = Long.parseLong(userIdStr.trim());
            Set<String> roles = parseRoles(request.getHeader(HEADER_USER_ROLES));
            UserContext.set(new LoginUser(userId, roles));
            log.debug(">>>> [common-security] 用户上下文绑定成功 userId: {} roles: {}", userId, roles);
        } catch (NumberFormatException e) {
            log.warn(">>>> [common-security] X-User-Id Header 格式非法，跳过上下文绑定（请求将以匿名态继续）: {}", userIdStr);
        }
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void enforceLogin(HandlerMethod method) {
        if (resolveAnnotation(method, RequiresLogin.class) == null) {
            return;
        }
        if (UserContext.getLoginUser() == null) {
            log.warn(">>>> [common-security] 匿名请求访问登录保护接口，已拦截（接口: {}）: 401",
                    method.getMethod().getName());
            throw new BizException(ResultCodeEnum.UNAUTHORIZED);
        }
    }

    private void enforceRoles(HandlerMethod method) {
        RequiresRoles annotation = resolveAnnotation(method, RequiresRoles.class);
        if (annotation == null) {
            return;
        }
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            log.warn(">>>> [common-security] 匿名请求访问角色受限接口，已拦截（接口: {}）: 401",
                    method.getMethod().getName());
            throw new BizException(ResultCodeEnum.UNAUTHORIZED);
        }
        String[] required = annotation.value();
        boolean pass = annotation.logical() == Logical.OR
                ? loginUser.hasAnyRole(required)
                : loginUser.hasAllRoles(required);
        if (!pass) {
            log.warn(">>>> [common-security] 角色权限不足，访问拒绝（所需: {} 逻辑: {} 实际: {}）接口: {}",
                    Arrays.toString(required), annotation.logical(), loginUser.roles(), method.getMethod().getName());
            throw new BizException(ResultCodeEnum.FORBIDDEN);
        }
    }

    /**
     * 注解解析策略：方法级优先，类级兜底。
     * 方法注解可覆盖类注解，符合最小惊讶原则。
     */
    private <A extends Annotation> A resolveAnnotation(HandlerMethod method, Class<A> type) {
        A ann = method.getMethodAnnotation(type);
        return ann != null ? ann : method.getBeanType().getAnnotation(type);
    }
}
