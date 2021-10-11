package io.micronaut.docs.http.client.bind.annotation;

//tag::clazz[]
import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target(PARAMETER)
@Bindable
public @interface Metadata {
}
//end::clazz[]
