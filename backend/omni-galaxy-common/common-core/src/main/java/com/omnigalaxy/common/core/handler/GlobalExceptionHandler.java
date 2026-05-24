package com.omnigalaxy.common.core.handler;

import com.omnigalaxy.common.core.exception.BizException;
import com.omnigalaxy.common.core.result.Result;
import com.omnigalaxy.common.core.result.ResultCodeEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
 *   <li>{@link BizException} — 预期内业务异常，提取 code + msg 直接响应</li>
 *   <li>{@link MethodArgumentNotValidException} — @RequestBody 参数校验失败（400）</li>
 *   <li>{@link ConstraintViolationException} — @RequestParam/@PathVariable 参数校验失败（400）</li>
 *   <li>{@link Exception} — 预期外系统异常，统一返回 500 并完整记录堆栈</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn(">>>> [核心底座] 业务异常已拦截，预期内（不影响系统稳定性）: {}", e.getMessage());
        return Result.failed(e.getResultCode().getCode(), e.getMessage());
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
        return Result.failed(ResultCodeEnum.FAILED);
    }
}
