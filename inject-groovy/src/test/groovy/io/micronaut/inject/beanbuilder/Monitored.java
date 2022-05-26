package io.micronaut.inject.beanbuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@SomeInterceptorBinding
public @interface Monitored {
}
