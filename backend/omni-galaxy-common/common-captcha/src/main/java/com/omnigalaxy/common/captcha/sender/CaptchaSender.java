package com.omnigalaxy.common.captcha.sender;

public interface CaptchaSender {

    /**
     * 发送验证码到指定渠道与标识符。
     *
     * @param identityType 渠道类型（PHONE / EMAIL），大写枚举名
     * @param identifier   目标地址（E.164 格式手机号或邮箱）
     * @param code         验证码（通常为 6 位数字字符串）
     */
    void send(String identityType, String identifier, String code);
}