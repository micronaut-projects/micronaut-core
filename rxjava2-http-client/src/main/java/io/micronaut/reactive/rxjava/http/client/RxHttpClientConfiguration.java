package io.micronaut.reactive.rxjava.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

public class RxHttpClientConfiguration {
    private static RxHttpClientFactory clientFactory = null;

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @Internal
    static RxHttpClient createClient(@Nullable URL url) {
        RxHttpClientFactory clientFactory = getReactiveHttpClientFactory();
        return clientFactory.createClient(url);
    }

    private static RxHttpClientFactory resolveClientFactory() {
        final Iterator<RxHttpClientFactory> i = ServiceLoader.load(RxHttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No ReactorHttpClientFactory present on classpath, cannot create HTTP client");
    }

    private static RxHttpClientFactory getReactiveHttpClientFactory() {
        RxHttpClientFactory clientFactory = RxHttpClientConfiguration.clientFactory;
        if (clientFactory == null) {
            synchronized (RxHttpClientConfiguration.class) { // double check
                clientFactory = RxHttpClientConfiguration.clientFactory;
                if (clientFactory == null) {
                    clientFactory = resolveClientFactory();
                    RxHttpClientConfiguration.clientFactory = clientFactory;
                }
            }
        }
        return clientFactory;
    }

}
