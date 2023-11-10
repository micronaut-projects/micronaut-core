package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class NonNullBean {

    @NonNull
    private final String value;

    public NonNullBean(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public String getValue() {
        return value;
    }
}
