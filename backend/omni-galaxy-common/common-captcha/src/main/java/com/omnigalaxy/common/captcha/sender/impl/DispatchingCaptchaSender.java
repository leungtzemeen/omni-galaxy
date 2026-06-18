package com.omnigalaxy.common.captcha.sender.impl;

import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 验证码发送分发器：按 {@code identityType} 路由到对应的渠道实现。
 *
 * <p>由 {@link com.omnigalaxy.common.captcha.config.CaptchaAutoConfig} 在检测到
 * 至少一个渠道级 sender Bean 时自动装配，覆盖开发模式的 {@link ConsoleCaptchaSender}。
 *
 * <p>{@code senders} Map 的 key 为 identityType 大写字符串（如 "PHONE"、"EMAIL"），
 * 由 AutoConfig 在构建时注入，运行期只读。
 */
@Slf4j
@RequiredArgsConstructor
public class DispatchingCaptchaSender implements CaptchaSender {

    private final Map<String, CaptchaSender> senders;

    @Override
    public void send(String identityType, String identifier, String code) {
        String channel = identityType.toUpperCase();
        CaptchaSender sender = senders.get(channel);
        if (sender == null) {
            // 渠道未配置 sender：快速失败，暴露配置缺失问题，避免验证码静默丢失
            throw new IllegalStateException(
                    "未找到 identityType=" + channel + " 对应的 CaptchaSender，请检查 common-captcha 渠道配置");
        }
        sender.send(identityType, identifier, code);
    }
}
