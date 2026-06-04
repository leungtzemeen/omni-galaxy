package com.omnigalaxy.platform.auth.dto;

import com.omnigalaxy.common.core.validation.EitherNotBlank;
import com.omnigalaxy.common.core.validation.EmailFormat;
import com.omnigalaxy.common.core.validation.PhoneFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * NEED_BIND 后续自愈绑定请求：用户持 bindTicket + OTP 完成社交账号与既有账号的合并。
 *
 * <p>入参 phone/email 由用户在 UI 中手动输入完整值（非脱敏），与 bindTicket 内的
 * {@code conflictIdentifier} 做一致性校验，防止换号越权攻击。
 */
@Data
@EitherNotBlank(fieldNames = {"phone", "email"}, message = "{validation.otp.either.phoneOrEmail}")
public class BindSocialRequest {

    /** NEED_BIND 阶段下发的一次性绑定票据 */
    @NotBlank(message = "{validation.social.bindTicket.notBlank}")
    private String bindTicket;

    /** 用户手动输入的完整手机号（E.164），与 OTP 校验目标一致 */
    @PhoneFormat
    private String phone;

    /** 用户手动输入的完整邮箱，与 OTP 校验目标一致 */
    @EmailFormat
    private String email;

    /** 用户收到的 6 位数字验证码 */
    @NotBlank(message = "{validation.otp.code.notBlank}")
    @Pattern(regexp = "^\\d{6}$", message = "{validation.otp.code.pattern}")
    private String otpCode;

    public String getIdentifier() {
        return phone != null ? phone : email;
    }

    public String getIdentityType() {
        return phone != null ? "PHONE" : "EMAIL";
    }
}