package com.omnigalaxy.common.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IdentifierMatchesTypeValidator.class)
public @interface IdentifierMatchesType {

    String message() default "identifier format must match identityType (PHONE must be E.164, EMAIL must be valid email)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
