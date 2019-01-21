package io.micronaut.configuration.hibernate.validator;

import io.micronaut.core.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class NoBeanValidator implements ConstraintValidator<NoBean, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return StringUtils.isNotEmpty(value);
    }
}
