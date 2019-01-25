package io.micronaut.configuration.rabbitmq.annotation;

import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD}) // this can be either type or param
@Repeatable(value = RabbitProperties.class)
@Bindable
public @interface RabbitProperty {

    /**
     * If used as a bound parameter, this is the property name. If used on a class level this is value and
     * not the property name.
     * @return The name of the property, otherwise it is inferred from the {@link #name()}
     */
    String value() default "";

    /**
     * If used on a class level with @Properties this is the header name and value is the value.
     * @return The name of property
     */
    String name() default "";
}
