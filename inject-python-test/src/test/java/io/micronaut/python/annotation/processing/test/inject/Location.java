package io.micronaut.python.annotation.processing.test.inject;

import jakarta.inject.Qualifier;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Locations.class)
public @interface Location {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Locations {
    Location[] value();
}
