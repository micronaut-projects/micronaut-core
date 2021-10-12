package io.micronaut.validation.validator.constraints.custom;

import javax.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = { })
@interface AlwaysInvalidConstraint {
    String message() default "invalid";

    @Target(TYPE)
    @Retention(RUNTIME)
    @Documented
    @interface List {
        AlwaysInvalidConstraint[] value();
    }
}
