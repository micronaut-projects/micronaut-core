package io.micronaut.inject.any.qualifier;

import io.micronaut.context.Qualifier;
import org.jspecify.annotations.Nullable;

public record MyAnyEngineChild(@Nullable Qualifier<?> qualifier, MyAnyEngine engine) {
}
