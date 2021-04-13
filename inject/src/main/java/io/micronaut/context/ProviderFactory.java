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

    /**
     * Creates a provider of the requested type.
     *
     * @param providerType The provider type class
     * @param beanProvider The bean provider
     * @param <P> The provider type
     * @return An optional provider
     */
    @NonNull
    public static <P> Optional<P> createProvider(Class<P> providerType, @NonNull BeanProvider<?> beanProvider) {
        if (providerType == BeanProvider.class) {
            return Optional.of((P) beanProvider);
        }
        Function<Supplier<?>, ?> function = providers.get(providerType);
        if (function != null) {
            return Optional.ofNullable((P) function.apply(beanProvider::get));
        }
        return Optional.empty();
    }

    /**
     * @param clazz A class name
     * @return True if the class equals any of the supported
     * provider types
     */
    public static boolean isProvider(String clazz) {
        if (clazz.equals(BeanProvider.class.getName())) {
            return true;
        }
        for (Class provider: getProviders()) {
            if (provider.getName().equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param clazz A class
     * @return True if the class equals or extends any of the
     * supported provider types
     */
    public static boolean isProvider(Class clazz) {
        if (BeanProvider.class.isAssignableFrom(clazz)) {
            return true;
        }
        for (Class provider: getProviders()) {
            if (provider.isAssignableFrom(clazz)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The set of provider types
     */
    public static Set<Class> getProviders() {
        return providers.keySet();
    }
}
