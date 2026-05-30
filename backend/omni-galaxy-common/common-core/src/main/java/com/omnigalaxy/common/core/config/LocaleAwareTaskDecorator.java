package com.omnigalaxy.common.core.config;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskDecorator;

/**
 * 异步线程 Locale 上下文继承装饰器。
 *
 * <p>LocaleContextHolder 基于 ThreadLocal，默认不跨线程传播。
 * 将此装饰器注入自定义线程池（ThreadPoolTaskExecutor.setTaskDecorator）
 * 或 @Async 配置的 Executor，可确保子线程继承父线程的语言上下文。
 *
 * <p>示例：
 * <pre>{@code
 * executor.setTaskDecorator(new LocaleAwareTaskDecorator());
 * }</pre>
 */
public class LocaleAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        LocaleContext parentLocaleContext = LocaleContextHolder.getLocaleContext();
        return () -> {
            try {
                LocaleContextHolder.setLocaleContext(parentLocaleContext, true);
                runnable.run();
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }
        };
    }
}