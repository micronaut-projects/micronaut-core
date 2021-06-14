package io.micronaut.inject.annotation;

import jakarta.inject.Qualifier;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Documented
@Qualifier
public @interface ParamAnn {
}
