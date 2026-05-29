package com.omnigalaxy.platform.auth.service;

import com.omnigalaxy.platform.auth.dto.SendCodeRequest;

public interface AuthCodeService {

    /**
     * 生成 OTP 并（在未来）通过短信/邮件发送给用户。
     * 当前阶段以日志输出验证码供开发调试。
     */
    void sendCode(SendCodeRequest request);
}
