package com.omnigalaxy.common.core.handler;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.IResultCode;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常拦截器。
 * 按异常类型分层治理，策略解耦，全部格式化为统一响应体返回。
 *
 * <p>拦截优先级（由精确到兜底）：
 * <ol>
 *   <li>{@link BizException} — 预期内业务异常，反向桥接 MessageSource 解析多语言 msg</li>
 *   <li>{@link MethodArgumentNotValidException} — @RequestBody 参数校验失败（400）</li>
 *   <li>{@link ConstraintViolationException} — @RequestParam/@PathVariable 参数校验失败（400）</li>
 *   <li>{@link Exception} — 预期外系统异常，统一返回 500 并完整记录堆栈</li>
 * </ol>
 *
 * <p>反向桥接策略：以 {@code "code." + resultCode.name()} 为 key 查询 MessageSource，
 * 若 key 未配置则直接使用枚举自带的中文 msg 兜底，枚举本身无需任何改动。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        String msg = resolveMsg(e.getResultCode(), e.getArgs());
        log.warn(">>>> [核心底座] 业务异常已拦截，预期内（不影响系统稳定性）: {}", msg);
        return Result.failed(e.getResultCode().getCode(), msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.failed(ResultCodeEnum.VALIDATE_FAILED.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining("; "));
        return Result.failed(ResultCodeEnum.VALIDATE_FAILED.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error(">>>> [核心底座] 严重崩溃，未知系统异常，需立即排查", e);
        return Result.failed(ResultCodeEnum.FAILED.getCode(), resolveMsg(ResultCodeEnum.FAILED, null));
    }

    // -------------------------------------------------------------------------

    /**
     * i18n 统一解析网关：以 "code.{enum.name()}" 为 key 查 MessageSource，
     * key 缺失时降级使用枚举自带中文 msg 兜底，枚举本身无需改动。
     */
    private String resolveMsg(IResultCode resultCode, Object[] args) {
        if (resultCode.getClass().isEnum()) {
            String key = "code." + ((Enum<?>) resultCode).name();
            return messageSource.getMessage(key, args, resultCode.getMsg(), LocaleContextHolder.getLocale());
        }
        return resultCode.getMsg();
    }
}