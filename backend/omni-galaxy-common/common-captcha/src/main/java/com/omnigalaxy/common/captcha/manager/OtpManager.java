package com.omnigalaxy.common.captcha.manager;

import com.omnigalaxy.common.captcha.enums.OtpScene;

/**
 * OTP 生命周期管理器。
 * 职责：生成、存储、校验验证码及冷却期管控，不负责实际的短信/邮件投递（由调用方决策）。
 */
public interface OtpManager {

    /**
     * 生成 6 位随机数字码并存入 Redis（5 分钟有效），同时设置 1 分钟发送冷却锁。
     * 若冷却期内再次调用，抛出 BizException。
     *
     * @return 生成的验证码明文（供调用方通过短信/邮件发送给用户）
     */
    String generateAndStore(OtpScene scene, String identityType, String identifier);

    /**
     * 校验验证码。校验成功后立即删除 Redis key，防止重放攻击。
     *
     * @return true=通过，false=错误或已过期
     */
    boolean verify(OtpScene scene, String identityType, String identifier, String code);

    /**
     * 检查指定标识符是否在 1 分钟发送冷却期内。
     * 冷却 key 仅按 (identityType, identifier) 维度限制，与 scene 无关，
     * 防止切换 scene 绕过频率限制。
     */
    boolean isOnCooldown(String identityType, String identifier);
}
