package io.micronaut.context;

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Provider;

public class JakartaProviderFactory implements ProviderFactory<jakarta.inject.Provider> {

    @Override
    @NonNull
    public <T> Provider<T> createProvider(@NonNull io.micronaut.context.Provider<T> provider) {
        return provider::get;
    }

    @Override
    @NonNull
    public Class<Provider> getType() {
        return Provider.class;
    }
}
