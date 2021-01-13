package io.micronaut.context;

import org.jetbrains.annotations.NotNull;

import javax.inject.Provider;

public class JavaxProviderFactory implements ProviderFactory<Provider> {

    @NotNull
    @Override
    public <T> Provider<T> createProvider(@NotNull io.micronaut.context.Provider<T> provider) {
        return provider::get;
    }

    @NotNull
    @Override
    public Class<Provider> getType() {
        return Provider.class;
    }
}
