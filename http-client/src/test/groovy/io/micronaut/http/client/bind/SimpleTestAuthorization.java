package io.micronaut.http.client.bind;

import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(ElementType.METHOD)
@Bindable
@interface SimpleTestAuthorization {
}