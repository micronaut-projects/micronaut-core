package io.micronaut.validation.validator.customwithdefaultconstraints;

import javax.validation.Constraint;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = {})
@interface EmployeeExperienceConstraint {

    String message() default "invalid";
}
