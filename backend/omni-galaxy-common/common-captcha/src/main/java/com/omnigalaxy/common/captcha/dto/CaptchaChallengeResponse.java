package com.omnigalaxy.common.captcha.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图形验证码挑战：一次性，验证后无论结果如何立即失效（GETDEL 原子销毁）。
 */
@Data
@AllArgsConstructor
public class CaptchaChallengeResponse {

    /** 挑战ID，下次提交时随答案一起回传 */
    private String challengeId;

    /** 图形验证码图片（Base64，含 data:image 前缀） */
    private String imageBase64;
}