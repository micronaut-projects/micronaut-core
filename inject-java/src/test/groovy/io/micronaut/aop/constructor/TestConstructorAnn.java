package io.micronaut.aop.constructor;

import io.micronaut.aop.AroundConstruct;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@AroundConstruct
public @interface TestConstructorAnn {
}
