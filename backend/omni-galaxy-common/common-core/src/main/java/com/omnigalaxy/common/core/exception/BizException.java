package com.omnigalaxy.common.core.exception;

import com.omnigalaxy.common.core.result.IResultCode;
import lombok.Getter;

/**
 * 全局业务异常。
 * 强制绑定 IResultCode 枚举，禁止裸字符串构造，确保所有错误消息均可走 i18n 反向桥接。
 *
 * <p>示例：
 * <pre>{@code
 * throw new BizException(ResultCodeEnum.UNAUTHORIZED);
 * throw new BizException(AuthResultCodeEnum.OTP_INVALID);
 * }</pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final IResultCode resultCode;
    private final Object[]    args;

    public BizException(IResultCode resultCode) {
        this(resultCode, (Object[]) null);
    }

    public BizException(IResultCode resultCode, Object... args) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
        this.args = args;
    }
}
