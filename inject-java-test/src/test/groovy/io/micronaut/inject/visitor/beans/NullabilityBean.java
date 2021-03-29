package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class NullabilityBean {

    @NonNull
    String name;

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }


}
