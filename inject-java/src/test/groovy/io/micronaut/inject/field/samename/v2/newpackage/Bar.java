package io.micronaut.inject.field.samename.v2.newpackage;

import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.field.samename.v2.Abc;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "SameFieldPackageProtectedTest")
@Singleton
public class Bar {

    @Inject
    protected Abc abc;

    public Abc getBarAbc() {
        return abc;
    }
}
