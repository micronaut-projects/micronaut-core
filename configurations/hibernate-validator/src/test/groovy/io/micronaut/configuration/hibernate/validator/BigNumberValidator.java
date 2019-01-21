package io.micronaut.configuration.hibernate.validator;

import io.micronaut.context.BeanContext;

import javax.inject.Inject;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class BigNumberValidator implements ConstraintValidator<BigNumber, Integer> {

    @Inject
    BeanContext beanContext;

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        assert beanContext != null;
        return value > 5;
    }
}
