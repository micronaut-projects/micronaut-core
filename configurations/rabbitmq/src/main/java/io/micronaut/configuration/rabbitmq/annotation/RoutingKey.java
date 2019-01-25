package io.micronaut.configuration.rabbitmq.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.messaging.annotation.MessageMapping;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Bindable
public @interface RoutingKey {

    @AliasFor(annotation = MessageMapping.class, member = "value")
    String value();


}