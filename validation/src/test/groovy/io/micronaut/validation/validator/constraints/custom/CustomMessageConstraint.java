package io.micronaut.validation.validator.constraints.custom;

import javax.validation.Constraint;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = { })
@interface CustomMessageConstraint {
    String message() default "invalid";
}
