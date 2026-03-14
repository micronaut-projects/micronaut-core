package io.micronaut.inject.field.samename.v2;

import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.field.samename.v2.newpackage.Bar;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "SameFieldPackageProtectedTest")
@Singleton
public class Foo extends Bar {

    @Inject
    protected Abc abc;

    public Abc getFooAbc() {
        return abc;
    }
}
