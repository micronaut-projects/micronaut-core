package io.micronaut.inject.field.simpleinjection;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;

import javax.annotation.Nullable;

public class E {
    @Nullable
    @Value("${greeting}")
    private String value = "Default greeting";

    @Nullable
    @Property(name = "greeting")
    private String property = "Default greeting";

    String getValue() {
        return value;
    }

    String getProperty() {
        return property;
    }
}
