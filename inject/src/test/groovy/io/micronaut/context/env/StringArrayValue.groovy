package io.micronaut.context.env

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.RetentionPolicy.RUNTIME

@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@interface StringArrayValue {
    String[] value();
}
