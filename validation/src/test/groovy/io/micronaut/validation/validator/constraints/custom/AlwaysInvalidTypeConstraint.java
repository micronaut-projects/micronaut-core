package io.micronaut.validation.validator.constraints.custom;

import java.lang.annotation.Retention;
import javax.validation.Constraint;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = { })
@interface AlwaysInvalidTypeConstraint {
    String message() default "custom invalid type";
}
