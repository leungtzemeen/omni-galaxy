package com.omnigalaxy.common.core.result;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * i18n 状态码消息解析器。
 *
 * <p>统一收口"code.{枚举名}"→MessageSource 的查找逻辑，
 * key 缺失时以枚举自带中文 msg 兜底，兜底链由此处单点维护。
 * GlobalExceptionHandler、局部 @ExceptionHandler 均注入此 Bean，杜绝重复实现。
 */
@Component
@RequiredArgsConstructor
public class ResultCodeMessageResolver {

    private final MessageSource messageSource;

    /**
     * 解析状态码对应的本地化消息。
     *
     * @param resultCode 状态码，枚举类型走 MessageSource 查找，否则直接取 {@link IResultCode#getMsg()}
     * @param args       消息占位符参数，允许为 null
     */
    public String resolve(IResultCode resultCode, Object[] args) {
        if (resultCode.getClass().isEnum()) {
            String key = "code." + ((Enum<?>) resultCode).name();
            return messageSource.getMessage(key, args, resultCode.getMsg(), LocaleContextHolder.getLocale());
        }
        return resultCode.getMsg();
    }
}