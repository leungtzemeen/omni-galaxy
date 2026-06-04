package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.api.dto.LoginResponse;
import com.omnigalaxy.platform.auth.api.dto.SocialLoginResponse;
import com.omnigalaxy.platform.auth.dto.BindSocialRequest;
import com.omnigalaxy.platform.auth.dto.MiniProgramLoginRequest;

/**
 * 社交登录服务：编排 OAuth state 管理、code-exchange、账户生命周期三层逻辑。
 *
 * <p>各方法的关注点划分：
 * <ul>
 *   <li>state 生成与校验 → 委托 {@link com.omnigalaxy.platform.auth.component.OAuthStateManager}</li>
 *   <li>code 换取平台身份 → 委托 {@link com.omnigalaxy.platform.auth.oauth.SocialLoginStrategy}</li>
 *   <li>账户发现/注册/合并 → 委托 {@link com.omnigalaxy.platform.auth.component.AccountLifecycleManager}</li>
 *   <li>JWT 签发 → 委托 {@link com.omnigalaxy.platform.auth.util.JwtUtils}</li>
 * </ul>
 */
public interface SocialAuthService {

    // ── 登录态（未认证用户） ────────────────────────────────────────────────────

    /** 生成登录态 state，前端发起 OAuth 扫码/跳转前调用，防 CSRF。 */
    String generateLoginState(String provider);

    /**
     * 处理 PC 网页 OAuth 回调（登录/注册）。
     *
     * @param provider 渠道标识（大写），如 {@code WECHAT}
     * @param code     平台回调的一次性 code
     * @param state    平台原样回传的 state，用于 CSRF 校验
     * @return {@link SocialLoginResponse}，status=SUCCESS 直接登录，status=NEED_BIND 引导 OTP 验证
     */
    SocialLoginResponse handleWebCallback(String provider, String code, String state);

    /**
     * 处理小程序登录，支持携带加密手机号 hint。
     *
     * @return {@link SocialLoginResponse}，同上
     */
    SocialLoginResponse handleMiniProgramLogin(MiniProgramLoginRequest request);

    // ── 绑定态（已认证用户） ────────────────────────────────────────────────────

    /**
     * 生成绑定态 state（含 userId 绑定，防 OAuth 劫持绑定攻击）。
     *
     * @param provider      渠道标识（大写）
     * @param currentUserId 当前已登录用户的 userId（由网关透传，来自 JWT）
     */
    String generateBindState(String provider, Long currentUserId);

    /**
     * 处理已认证用户的社交账号绑定 OAuth 回调。
     *
     * <p>userId 从 state 中提取（而非 JWT Header），因为 OAuth 回调是浏览器重定向，不携带 Token。
     * state 的 userId 绑定即 CSRF 防线。
     */
    void handleBindCallback(String provider, String code, String state);

    /**
     * 通过 OTP 消费 bindTicket，完成 NEED_BIND 自愈绑定并签发 Token。
     *
     * <p>适用于社交登录触发信任域冲突（NEED_BIND）后的第二阶段验证流程。
     */
    LoginResponse bindByOtp(BindSocialRequest request);
}