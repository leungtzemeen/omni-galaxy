package com.omnigalaxy.common.core.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 全局基础状态码枚举。
 * 仅收录平台级通用状态码，业务域专属错误码由各域自行实现 IResultCode 扩展。
 */
@Getter
@RequiredArgsConstructor
public enum ResultCodeEnum implements IResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "系统未知异常"),
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "请先登录"),
    FORBIDDEN(403, "无访问权限，请联系管理员授权");

    private final int code;
    private final String msg;
}
