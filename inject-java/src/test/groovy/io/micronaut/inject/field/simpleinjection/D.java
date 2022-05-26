package io.micronaut.inject.field.simpleinjection;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;

import javax.annotation.Nullable;

public class D {
    @Nullable
    @Value("${greeting}")
    protected String value = "Default greeting";

    @Nullable
    @Property(name = "greeting")
    protected String property = "Default greeting";

    String getValue() {
        return value;
    }

    String getProperty() {
        return property;
    }
}
