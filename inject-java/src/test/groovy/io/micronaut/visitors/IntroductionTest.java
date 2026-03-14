package io.micronaut.visitors;

import io.micronaut.aop.Introduction;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Introduction
@Retention(RetentionPolicy.RUNTIME)
public @interface IntroductionTest {
    String value() default "";
}
