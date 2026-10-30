package io.micronaut.docs.aop.lifecycle.pertarget.labelled;

import io.micronaut.aop.Around;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)
public @interface Labelled {
}
