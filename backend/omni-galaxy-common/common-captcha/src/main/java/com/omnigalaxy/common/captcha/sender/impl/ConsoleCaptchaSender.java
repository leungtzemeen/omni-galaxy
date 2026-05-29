package com.omnigalaxy.common.captcha.sender.impl;

import com.omnigalaxy.common.captcha.sender.CaptchaSender;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsoleCaptchaSender implements CaptchaSender {

    @Override
    public void send(String identifier, String code) {
        log.info("<<<< [CaptchaSender] 验证码已生成（控制台打印模式，生产环境需对接 SMS/Email 服务） identifier: {} code: {}",
                 identifier, code);
    }
}