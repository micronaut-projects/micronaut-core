package io.micronaut.configuration.hibernate.validator;

import javax.validation.Payload;

import javax.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = NoBeanValidator.class)
@Documented
public @interface NoBean {

    String message() default "The class isn't a bean";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
