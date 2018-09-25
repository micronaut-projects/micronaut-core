package io.micronaut.inject.qualifiers.compose;

import io.micronaut.context.annotation.DefaultScope;

import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@DefaultScope(Singleton.class)
public @interface Composable {}
