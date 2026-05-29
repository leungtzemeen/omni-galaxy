package com.omnigalaxy.common.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;

public class EitherNotBlankValidator implements ConstraintValidator<EitherNotBlank, Object> {

    private String[] fieldNames;

    @Override
    public void initialize(EitherNotBlank constraintAnnotation) {
        this.fieldNames = constraintAnnotation.fieldNames();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        int nonBlankCount = 0;
        for (String fieldName : fieldNames) {
            try {
                Field field = value.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object fieldValue = field.get(value);
                if (fieldValue != null && !fieldValue.toString().isBlank()) {
                    nonBlankCount++;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return nonBlankCount == 1;
    }
}