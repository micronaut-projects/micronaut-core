package io.micronaut.configuration.rabbitmq.annotation;

import io.micronaut.aop.Introduction;
import io.micronaut.configuration.rabbitmq.intercept.RabbitMQIntroductionAdvice;
import io.micronaut.context.annotation.Type;
import io.micronaut.retry.annotation.Recoverable;

import javax.inject.Scope;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Scope
@Introduction
@Type(RabbitMQIntroductionAdvice.class)
@Recoverable
@Singleton
public @interface RabbitClient {

    String value() default "";

}
