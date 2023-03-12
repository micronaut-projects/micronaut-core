package io.micronaut.inject.visitor.beans.reflection;

import io.micronaut.core.annotation.Introspected;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.OptionalInt;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class OptionalIntHolder {
    private final @NotNull @Min(5L) OptionalInt optionalInt;

    OptionalIntHolder(OptionalInt optionalInt) {
        this.optionalInt = optionalInt;
    }
}
