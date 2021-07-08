package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

@Internal
class HttpClientFactoryResolver {

    private static HttpClientFactory factory;

    @NonNull
    static HttpClient createClient(URL url) {
        return getFactory().createClient(url);
    }

    @NonNull
    static HttpClient createClient(URL url, HttpClientConfiguration configuration) {
        return getFactory().createClient(url, configuration);
    }

    private static HttpClientFactory getFactory() {
        if (factory == null) {
            synchronized (HttpClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static HttpClientFactory resolveClientFactory() {
        final Iterator<HttpClientFactory> i = ServiceLoader.load(HttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No HttpClientFactory present on classpath, cannot create client");
    }
}
