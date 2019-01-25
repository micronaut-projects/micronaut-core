package io.micronaut.configuration.rabbitmq.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Queue {

    String value();

    boolean reQueue() default false;
}
