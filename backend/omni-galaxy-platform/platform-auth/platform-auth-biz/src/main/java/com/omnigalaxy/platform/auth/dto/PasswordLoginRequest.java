package com.omnigalaxy.platform.auth.dto;

import com.omnigalaxy.common.core.validation.PasswordStrength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(title = "密码登录请求", description = "账号密码登录，待 Phase 2 实现")
public class PasswordLoginRequest {

    @NotBlank(message = "{validation.account.notBlank}")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,10}$", message = "{validation.account.pattern}")
    @Schema(description = "账号（英文和数字，4-10 位）", example = "user1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String account;

    @NotBlank(message = "{validation.password.notBlank}")
    @PasswordStrength
    @Schema(description = "密码（8-20 位，需包含大小写字母和数字）", example = "Pass1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
