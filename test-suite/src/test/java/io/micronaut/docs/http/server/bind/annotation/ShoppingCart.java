package io.micronaut.docs.http.server.bind.annotation;

// tag::class[]
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RUNTIME)
@Bindable //<1>
public @interface ShoppingCart {

    @AliasFor(annotation = Bindable.class, member = "value")
    String value() default "";
}

// end::class[]
