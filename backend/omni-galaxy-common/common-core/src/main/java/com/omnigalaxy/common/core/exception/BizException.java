package com.omnigalaxy.common.core.exception;

import com.omnigalaxy.common.core.result.IResultCode;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import lombok.Getter;

/**
 * 全局业务异常。
 * 携带 IResultCode 契约，支持自定义 msg 覆盖枚举默认描述，供各业务域精确上报错误上下文。
 *
 * <p>示例：
 * <pre>{@code
 * throw new BizException(ResultCodeEnum.FAILED, "用户余额不足以支付该订单");
 * throw new BizException(MallResultCodeEnum.STOCK_NOT_ENOUGH);
 * }</pre>
 */
@Getter
public class BizException extends RuntimeException {

    private final IResultCode resultCode;

    public BizException(IResultCode resultCode) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
    }

    public BizException(IResultCode resultCode, String msg) {
        super(msg);
        this.resultCode = resultCode;
    }

    /** 快捷构造：默认使用 FAILED(500) 作为错误码，仅需传入业务上下文描述 */
    public BizException(String msg) {
        super(msg);
        this.resultCode = ResultCodeEnum.FAILED;
    }
}
