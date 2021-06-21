package io.micronaut.inject.dependent;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding(kind = InterceptorKind.AROUND)
public @interface TestAnn {
}
