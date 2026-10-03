package io.micronaut.aop.adapter.classlevel;

import io.micronaut.aop.Adapter;
import io.micronaut.core.annotation.Indexed;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Adapter(TracedListener.class)
@Indexed(TracedListener.class)
@interface TracedListenerMethod {
}
