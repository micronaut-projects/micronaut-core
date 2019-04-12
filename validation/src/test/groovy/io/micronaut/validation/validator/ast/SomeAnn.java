package io.micronaut.validation.validator.ast;

import javax.validation.constraints.NotBlank;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
public @interface SomeAnn {
    @NotBlank
    String value();
}
