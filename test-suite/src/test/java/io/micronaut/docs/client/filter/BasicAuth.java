package io.micronaut.docs.client.filter;

import io.micronaut.http.annotation.HttpFilterQualifier;

import java.lang.annotation.*;

@HttpFilterQualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PARAMETER})
public @interface BasicAuth {
}
