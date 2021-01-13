package io.micronaut.context;

import edu.umd.cs.findbugs.annotations.NonNull;

public interface ProviderFactory<P> {

    @NonNull
    <T> P createProvider(@NonNull io.micronaut.context.Provider<T> provider);

    @NonNull
    Class<P> getType();
}
