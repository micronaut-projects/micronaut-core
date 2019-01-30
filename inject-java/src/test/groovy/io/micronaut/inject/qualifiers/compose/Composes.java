package io.micronaut.inject.qualifiers.compose;

import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Primary;

import javax.inject.Singleton;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Primary
@Retention(RUNTIME)
@DefaultScope(Singleton.class)
public @interface Composes {
    Class<?> value();
}
