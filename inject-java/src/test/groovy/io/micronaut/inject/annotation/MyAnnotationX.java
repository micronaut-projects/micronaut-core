package io.micronaut.inject.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
public @interface MyAnnotationX {

    Class<?> clazz1();

    Class<?> clazz2();

    Class<?> clazz3();

    Class<?> clazz4();

    Class<?> clazz5();

    Class<?> clazz6();

    Class<?> clazz7();

    Class<?> clazz8();

    Class<?> clazz9();

}
