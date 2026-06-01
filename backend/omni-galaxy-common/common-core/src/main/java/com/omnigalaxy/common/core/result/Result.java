package com.omnigalaxy.common.core.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * 全项目统一泛型响应体。
 * 只允许通过语义化静态工厂方法构建，严禁外部直接实例化。
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private final int code;
    private final String msg;
    private final T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ======================== 成功系列 ========================

    public static <T> Result<T> success() {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getMsg(), data);
    }

    /** 允许覆盖默认成功提示语，适用于"保存成功"等个性化场景；msg 须由调用方经 MessageSource 解析后传入 */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(ResultCodeEnum.SUCCESS.getCode(), msg, data);
    }

    // ======================== 失败系列 ========================

    /** 桥接 BizException 场景：保留原始枚举 code 语义，msg 须由调用方经 MessageSource 解析后传入 */
    public static <T> Result<T> failed(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    // ======================== 工具方法 ========================

    public boolean isSuccess() {
        return this.code == ResultCodeEnum.SUCCESS.getCode();
    }
}
