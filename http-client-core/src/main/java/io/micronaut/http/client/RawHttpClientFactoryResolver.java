package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;

import java.util.Iterator;
import java.util.ServiceLoader;

@Internal
final class RawHttpClientFactoryResolver {
    private static volatile RawHttpClientFactory factory;

    static RawHttpClientFactory getFactory() {
        if (factory == null) {
            synchronized (RawHttpClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static RawHttpClientFactory resolveClientFactory() {
        final Iterator<RawHttpClientFactory> i = ServiceLoader.load(RawHttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No RawHttpClientFactory present on classpath, cannot create client");
    }
}
