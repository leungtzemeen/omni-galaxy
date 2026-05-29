package com.omnigalaxy.platform.auth.dto;

import com.omnigalaxy.common.core.validation.IdentifierMatchesType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送验证码请求")
@IdentifierMatchesType
public class SendCodeRequest {

    @Schema(description = "凭证类型", example = "PHONE", allowableValues = {"PHONE", "EMAIL"})
    @NotBlank(message = "凭证类型不能为空")
    private String identityType;

    @Schema(description = "登录标识（手机号须 E.164 格式，如 +8613800138000；邮箱如 user@example.com）",
            example = "+8613800138000")
    @NotBlank(message = "登录标识不能为空")
    private String identifier;

    @Schema(description = "验证码场景", example = "LOGIN",
            allowableValues = {"LOGIN", "REGISTER", "BIND_PHONE", "BIND_EMAIL", "RESET_PWD"})
    @NotBlank(message = "验证码场景不能为空")
    private String scene;
}
