package com.omnigalaxy.common.captcha.sender;

public interface CaptchaSender {

    /**
     * 发送验证码到指定标识符（手机号/邮箱）。
     *
     * @param identifier 目标地址（E.164 格式手机号或邮箱）
     * @param code       验证码（通常为 6 位数字字符串）
     */
    void send(String identifier, String code);
}