package io.micronaut.docs.http.client.bind.method;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

//tag::clazz[]
@Documented
@Retention(RUNTIME)
@Target(METHOD) // <1>
@Bindable
public @interface NameAuthorization {
    @AliasFor(member = "name")
    String value() default "";

    @AliasFor(member = "value")
    String name() default "";
}
//end::clazz[]
