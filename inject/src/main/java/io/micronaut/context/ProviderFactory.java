/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility class to convert provider instances to the
 * requested provider type.
 *
 * Internal usage only.
 *
 * @author James Kleeh
 * @since 2.4.0
 */
@Internal
public class ProviderFactory {

    private static Map<Class, Function<Supplier<?>, ?>> providers = new HashMap<>(3);

    static {
        try {
            providers.put(javax.inject.Provider.class, supplier -> (javax.inject.Provider) supplier::get);
        } catch (NoClassDefFoundError e) {

        }
        try {
            providers.put(jakarta.inject.Provider.class, supplier -> (jakarta.inject.Provider) supplier::get);
        } catch (NoClassDefFoundError e) {

        }
    }

    @NonNull
    public static <P> Optional<P> createProvider(Class<P> providerType, @NonNull io.micronaut.context.Provider<?> provider) {
        if (providerType == io.micronaut.context.Provider.class) {
            return Optional.of((P) provider);
        }
        Function<Supplier<?>, ?> function = providers.get(providerType);
        if (function != null) {
            return Optional.ofNullable((P) function.apply(provider::get));
        }
        return Optional.empty();
    }

    public static boolean isProvider(String clazz) {
        for (Class provider: getProviders()) {
            if (provider.getName().equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProvider(Class clazz) {
        for (Class provider: getProviders()) {
            if (provider.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Class> getProviders() {
        return providers.keySet();
    }
}
