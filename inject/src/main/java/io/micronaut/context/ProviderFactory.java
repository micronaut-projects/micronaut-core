package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@Internal
public class ProviderFactory {

    private static Map<String, Function<Supplier<?>, ?>> providers = new HashMap<>(3);

    static {
        try {
            providers.put(javax.inject.Provider.class.getName(), supplier -> (javax.inject.Provider) supplier::get);
        } catch (NoClassDefFoundError e) {

        }
        try {
            providers.put(jakarta.inject.Provider.class.getName(), supplier -> (jakarta.inject.Provider) supplier::get);
        } catch (NoClassDefFoundError e) {

        }
    }

    @NonNull
    public static <P> Optional<P> createProvider(Class<P> providerType, @NonNull io.micronaut.context.Provider<?> provider) {
        if (providerType == io.micronaut.context.Provider.class) {
            return Optional.of((P) provider);
        }
        Function<Supplier<?>, ?> function = providers.get(providerType.getName());
        if (function != null) {
            return Optional.ofNullable((P) function.apply(provider::get));
        }
        return Optional.empty();
    }

    public static boolean isProvider(String clazz) {
        return providers.containsKey(clazz);
    }

    public static Set<String> getProviders() {
        return providers.keySet();
    }
}
