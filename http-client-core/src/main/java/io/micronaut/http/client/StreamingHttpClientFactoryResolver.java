package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

@Internal
class StreamingHttpClientFactoryResolver {

    private static StreamingHttpClientFactory factory;

    @NonNull
    static StreamingHttpClient createClient(URL url) {
        return getFactory().createStreamingClient(url);
    }

    @NonNull
    static StreamingHttpClient createClient(URL url, HttpClientConfiguration configuration) {
        return getFactory().createStreamingClient(url, configuration);
    }

    private static StreamingHttpClientFactory getFactory() {
        if (factory == null) {
            synchronized (StreamingHttpClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static StreamingHttpClientFactory resolveClientFactory() {
        final Iterator<StreamingHttpClientFactory> i = ServiceLoader.load(StreamingHttpClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No HttpClientFactory present on classpath, cannot create client");
    }
}
