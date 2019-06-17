package io.micronaut.core.annotation.beans.introduction;

import io.micronaut.aop.Introduction;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Introduction
//@Type(StubIntroducer.class)
@Documented
@Retention(RUNTIME)
public @interface Stub {
}

