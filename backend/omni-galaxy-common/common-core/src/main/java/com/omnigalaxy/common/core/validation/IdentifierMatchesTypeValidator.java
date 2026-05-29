package com.omnigalaxy.common.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;
import java.util.regex.Pattern;

public class IdentifierMatchesTypeValidator implements ConstraintValidator<IdentifierMatchesType, Object> {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+\\d{1,3}\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        try {
            Field identityTypeField = value.getClass().getDeclaredField("identityType");
            Field identifierField = value.getClass().getDeclaredField("identifier");

            identityTypeField.setAccessible(true);
            identifierField.setAccessible(true);

            String identityType = (String) identityTypeField.get(value);
            String identifier = (String) identifierField.get(value);

            // If either is blank, let other validators handle it
            if (identityType == null || identityType.isBlank() || identifier == null || identifier.isBlank()) {
                return true;
            }

            if ("PHONE".equalsIgnoreCase(identityType)) {
                return PHONE_PATTERN.matcher(identifier).matches();
            } else if ("EMAIL".equalsIgnoreCase(identityType)) {
                return EMAIL_PATTERN.matcher(identifier).matches();
            }

            return false;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return true;
        }
    }
}
