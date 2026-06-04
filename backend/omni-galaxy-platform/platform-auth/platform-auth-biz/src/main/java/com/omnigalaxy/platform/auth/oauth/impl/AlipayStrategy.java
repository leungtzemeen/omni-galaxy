package com.omnigalaxy.platform.auth.oauth.impl;

import com.omnigalaxy.platform.auth.oauth.SocialIdentity;
import com.omnigalaxy.platform.auth.oauth.SocialLoginStrategy;
import org.springframework.stereotype.Component;

/**
 * 支付宝渠道登录策略（占位实现，待 Phase 3 落地）。
 *
 * <h3>待实现内容</h3>
 * <ol>
 *   <li>调用支付宝开放平台 {@code alipay.system.oauth.token} 接口换取 user_id。</li>
 *   <li>可选：调用 {@code alipay.user.info.share} 获取用户昵称和头像。</li>
 *   <li>在 {@code application.yml} 中配置 {@code alipay.app-id}、{@code alipay.private-key} 等。</li>
 * </ol>
 */
@Component
public class AlipayStrategy implements SocialLoginStrategy {

    @Override
    public String identityType() {
        return "ALIPAY";
    }

    @Override
    public SocialIdentity exchangeWebCode(String code) {
        // TODO: 支付宝 OAuth 接入（Phase 3）
        throw new UnsupportedOperationException("支付宝渠道暂未开放，敬请期待");
    }
}