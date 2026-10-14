package io.micronaut.inject.registration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.jspecify.annotations.Nullable;

@Factory
public class NullBoxFactory {

    @Prototype
    @Nullable
    NullableBox nullableBox() {
        return null;
    }
}
