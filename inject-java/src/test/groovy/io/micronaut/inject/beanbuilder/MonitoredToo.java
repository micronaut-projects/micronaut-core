package io.micronaut.inject.beanbuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.micronaut.inject.beanbuilder.another.AnotherInterceptorBinding;

@AnotherInterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitoredToo {
}
