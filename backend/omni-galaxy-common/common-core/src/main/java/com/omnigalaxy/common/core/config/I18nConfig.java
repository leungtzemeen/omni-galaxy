package com.omnigalaxy.common.core.config;

import com.omnigalaxy.common.core.result.ResultCodeMessageResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 国际化核心配置。
 *
 * <p>职责：
 * <ol>
 *   <li>定义 MessageSource bean，basename 指向 i18n/messages，默认语言中文</li>
 *   <li>桥接 LocalValidatorFactoryBean，使 Bean Validation 的 {key} 统一走 MessageSource 解析</li>
 * </ol>
 *
 * <p>Locale 解析由 Spring MVC 自带的 AcceptHeaderLocaleResolver 负责（读取 Accept-Language header），
 * 无需在此重复定义，避免与 WebMvcAutoConfiguration 的同名 bean 冲突。
 */
@AutoConfiguration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        source.setFallbackToSystemLocale(false);
        source.setCacheSeconds(3600);
        return source;
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }

    @Bean
    public ResultCodeMessageResolver resultCodeMessageResolver(MessageSource messageSource) {
        return new ResultCodeMessageResolver(messageSource);
    }

}