package io.micronaut.inject.visitor.beans.reflection;

import io.micronaut.core.annotation.Introspected;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.util.OptionalDouble;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class OptionalDoubleHolder {
    private final @NotNull @DecimalMin("5") OptionalDouble optionalDouble;

    OptionalDoubleHolder(OptionalDouble optionalDouble) {
        this.optionalDouble = optionalDouble;
    }
}
