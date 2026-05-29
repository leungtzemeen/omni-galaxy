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
@Constraint(validatedBy = EmailFormatValidator.class)
@Documented
public @interface EmailFormat {

    String message() default "email must be a valid email address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}