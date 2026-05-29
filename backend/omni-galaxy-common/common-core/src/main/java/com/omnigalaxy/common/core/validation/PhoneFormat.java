package com.omnigalaxy.common.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneFormatValidator.class)
@Documented
public @interface PhoneFormat {

    String message() default "phone must be in E.164 format (e.g., +86 13800138000)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}