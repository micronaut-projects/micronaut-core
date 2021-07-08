package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

@Internal
class ProxyHttpClientFactoryResolver {

    private static ProxyHttpClientFactory factory;

    @NonNull
    static ProxyHttpClient createClient(URL url) {
        return getFactory().createProxyClient(url);
    }

    @NonNull
    static ProxyHttpClient createClient(URL url, HttpClientConfiguration configuration) {
        return getFactory().createProxyClient(url, configuration);
    }

    private static ProxyHttpClientFactory getFactory() {
        if (factory == null) {
            synchronized (ProxyHttpClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static ProxyHttpClientFactory resolveClientFactory() {
        final Iterator<ProxyHttpClientFactory> i = ServiceLoader.load(ProxyHttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No ProxyHttpClientFactory present on classpath, cannot create client");
    }
}
