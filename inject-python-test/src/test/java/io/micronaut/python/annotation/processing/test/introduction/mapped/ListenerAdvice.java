package io.micronaut.python.annotation.processing.test.introduction.mapped;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.event.ApplicationEventListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Introduction(interfaces = ApplicationEventListener.class)
@Type(ListenerAdviceInterceptor.class)
@Executable
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ListenerAdvice {
}
