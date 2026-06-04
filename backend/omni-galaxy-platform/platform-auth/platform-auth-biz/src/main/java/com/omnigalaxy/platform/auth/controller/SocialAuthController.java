package com.omnigalaxy.platform.auth.controller;

import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.dto.SocialLoginResponse;
import com.omnigalaxy.platform.auth.dto.BindSocialRequest;
import com.omnigalaxy.platform.auth.dto.MiniProgramLoginRequest;
import com.omnigalaxy.platform.auth.service.SocialAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社交登录控制器。
 *
 * <h3>端点分组</h3>
 * <ul>
 *   <li><b>登录态</b>（无需 JWT）：state 生成 + OAuth 回调 + 小程序登录</li>
 *   <li><b>绑定态</b>（需要 JWT，通过网关透传 X-User-Id）：state 生成 + OAuth 回调 + OTP 绑定</li>
 * </ul>
 *
 * <h3>CSRF 防御说明</h3>
 * <p>绑定态 state 由 {@code generateBindState} 生成时写入 {@code currentUserId}，
 * {@code handleBindCallback} 从 state 中提取 userId（不依赖 JWT Header），
 * 使攻击者无法通过构造回调 URL 将自己的社交账号绑定到受害者账号。
 */
@Tag(name = "社交登录", description = "OAuth 2.0 第三方渠道登录与账号绑定")
@RestController
@RequestMapping("/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    // =========================================================================
    // 登录态（未认证用户）
    // =========================================================================

    // ⚠️ 安全契约（网关层必须落地）：
    // 本端点每次调用写入一条 Redis state 记录，攻击者可用自动化脚本批量刷取制造 OOM。
    // 生产环境必须在 platform-gateway 配置：
    //   1. 基于客户端 IP 的 Rate Limiting（建议 10 次/分钟/IP）；
    //   2. 小程序侧高频场景可叠加前置风控行为验证（Captcha / 设备指纹）。
    @Operation(summary = "生成登录态 state",
               description = "前端在发起 OAuth 授权跳转前调用，获取防 CSRF 的 state 参数。state 有效期 5 分钟，一次性消费。" +
                             "【网关必配】IP 维度 Rate Limiting，防 Redis 僵尸 state OOM。")
    @GetMapping("/login/state")
    public Result<String> getLoginState(
            @Parameter(description = "渠道标识，大写，如 WECHAT / ALIPAY") @RequestParam String provider) {
        return Result.success(socialAuthService.generateLoginState(provider));
    }

    @Operation(summary = "PC 网页 OAuth 回调（登录/注册）",
               description = "微信/支付宝等平台授权后重定向至此端点。完成 CSRF 校验、code-exchange、账户登注。" +
                             "返回 SUCCESS + Token 或 NEED_BIND + bindTicket（信任域冲突时）。")
    @GetMapping("/login/callback")
    public Result<SocialLoginResponse> loginCallback(
            @Parameter(description = "渠道标识") @RequestParam String provider,
            @Parameter(description = "平台回调的一次性 code") @RequestParam String code,
            @Parameter(description = "原样透传的 state，用于 CSRF 校验") @RequestParam String state) {
        return Result.success(socialAuthService.handleWebCallback(provider, code, state));
    }

    @Operation(summary = "小程序登录",
               description = "处理微信小程序登录，支持携带加密手机号 hint 进行多凭证同步落地。" +
                             "手机号属于 EXTERNAL 信任级别，冲突时触发 NEED_BIND 流程而非自动合并。")
    @PostMapping("/mini/login")
    public Result<SocialLoginResponse> miniProgramLogin(
            @Valid @RequestBody MiniProgramLoginRequest request) {
        return Result.success(socialAuthService.handleMiniProgramLogin(request));
    }

    // =========================================================================
    // 绑定态（已认证用户，需 JWT，由网关解析并透传 X-User-Id）
    // =========================================================================

    // ⚠️ 安全契约（网关层必须落地）：
    // 虽已要求 X-User-Id（认证用户），仍须配置 userId 维度 Rate Limiting（建议 5 次/分钟/userId），
    // 防止已认证账号被脚本批量刷取写入过多绑定态 state 占用 Redis。
    @Operation(summary = "生成绑定态 state（已登录）",
               description = "已登录用户发起社交账号绑定前调用。state 内绑定当前 userId，" +
                             "OAuth 回调时校验以防 OAuth 劫持绑定攻击（Binding CSRF）。" +
                             "【网关必配】userId 维度 Rate Limiting。")
    @GetMapping("/bind/state")
    public Result<String> getBindState(
            @Parameter(description = "渠道标识") @RequestParam String provider,
            @Parameter(description = "当前登录用户 ID，由网关从 JWT 解析后透传", required = true)
            @RequestHeader("X-User-Id") Long currentUserId) {
        return Result.success(socialAuthService.generateBindState(provider, currentUserId));
    }

    @Operation(summary = "社交账号绑定 OAuth 回调（已登录）",
               description = "已登录用户完成 OAuth 授权后的回调。userId 从绑定态 state 中提取（回调不携带 JWT）。" +
                             "校验 state 携带的 userId 与绑定发起方一致，防 OAuth 劫持绑定攻击。")
    @GetMapping("/bind/callback")
    public Result<Void> bindCallback(
            @Parameter(description = "渠道标识") @RequestParam String provider,
            @Parameter(description = "平台回调的一次性 code") @RequestParam String code,
            @Parameter(description = "原样透传的绑定态 state（内嵌 userId）") @RequestParam String state) {
        socialAuthService.handleBindCallback(provider, code, state);
        return Result.success();
    }

    @Operation(summary = "OTP 消费 bindTicket 完成绑定",
               description = "社交登录触发 NEED_BIND 后的第二阶段自愈接口。" +
                             "用户输入完整手机号/邮箱并完成 OTP 验证，系统将社交凭证追加至既有账号并签发 Token。" +
                             "无需 JWT，bindTicket 即凭证。")
    @PostMapping("/bind/otp")
    public Result<LoginResponse> bindByOtp(@Valid @RequestBody BindSocialRequest request) {
        return Result.success(socialAuthService.bindByOtp(request));
    }
}