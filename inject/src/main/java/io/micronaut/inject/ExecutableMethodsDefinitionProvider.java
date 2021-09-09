package io.micronaut.inject;

import io.micronaut.core.annotation.Nullable;

public interface ExecutableMethodsDefinitionProvider {

    @Nullable
    ExecutableMethodsDefinition getExecutableMethodsDefinition();

}
