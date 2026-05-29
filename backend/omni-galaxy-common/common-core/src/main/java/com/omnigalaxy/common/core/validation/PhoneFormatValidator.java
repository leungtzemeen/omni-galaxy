package com.omnigalaxy.common.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PhoneFormatValidator implements ConstraintValidator<PhoneFormat, String> {

    private static final Pattern E164_PATTERN = Pattern.compile("^\\+\\d{1,3}\\d{6,14}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return E164_PATTERN.matcher(value).matches();
    }
}