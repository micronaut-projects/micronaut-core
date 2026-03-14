package io.micronaut.inject.field.samename.v1;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "SameFieldProtectedTest")
@Singleton
public class Bar {

    @Inject
    protected Abc abc;

    public Abc getBarAbc() {
        return abc;
    }
}
