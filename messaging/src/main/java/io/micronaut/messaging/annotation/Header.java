package io.micronaut.messaging.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.*;

/**
 * <p>An annotation that can be applied to method argument to indicate that the method argument is bound from a message header.</p>
 *
 * <p><This also can be used in conjection with @Headers to list headers on a client class that will always be applied./p>
 *
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.PARAMETER, ElementType.TYPE, ElementType.METHOD}) // this can be either type or param
@Repeatable(value = Headers.class)
@Bindable
public @interface Header {

    /**
     * If used as a bound parameter, this is the header name. If used on a class level this is value and not the header name.
     * @return The name of the header, otherwise it is inferred from the parameter name
     */
    String value() default "";

    /**
     * If used on a class level with @Headers this is the header name and value is the value.
     * @return name of header when using with @Headers
     */
    String name() default "";

}

