package io.micronaut.inject.records;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

@Introspected
@Requires(property = "spec.name", value = "RecordBeansSpec")
record Test3(@Inject @Nullable @NotNull MissingBean missingBean) {
}
