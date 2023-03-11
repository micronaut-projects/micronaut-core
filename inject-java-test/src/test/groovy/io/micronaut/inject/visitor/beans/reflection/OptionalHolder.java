package io.micronaut.inject.visitor.beans.reflection;

import io.micronaut.core.annotation.Introspected;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class OptionalHolder {
    private final Optional<@NotNull @NotBlank String> optional;

    OptionalHolder(Optional<String> optional) {
        this.optional = optional;
    }
}
