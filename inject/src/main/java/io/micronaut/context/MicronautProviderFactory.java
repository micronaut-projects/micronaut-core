package io.micronaut.context;

import org.jetbrains.annotations.NotNull;

public class MicronautProviderFactory implements ProviderFactory<Provider> {

    @NotNull
    @Override
    public <T> Provider<T> createProvider(@NotNull Provider<T> provider) {
        return provider;
    }

    @NotNull
    @Override
    public Class<Provider> getType() {
        return Provider.class;
    }
}
