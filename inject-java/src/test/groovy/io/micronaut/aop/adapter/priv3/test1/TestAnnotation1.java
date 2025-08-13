package io.micronaut.aop.adapter.priv3.test1;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@InterceptorBinding(kind = InterceptorKind.AROUND)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestAnnotation1 {
}
