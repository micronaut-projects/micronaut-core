package io.micronaut.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.StreamingHttpClientFactory;

import java.net.URL;
import java.util.Iterator;
import java.util.ServiceLoader;

@Internal
class WebSocketClientFactoryResolver {

    private static WebSocketClientFactory factory;

    @NonNull
    static WebSocketClient createClient(URL url) {
        return getFactory().createWebSocketClient(url);
    }

    @NonNull
    static WebSocketClient createClient(URL url, HttpClientConfiguration configuration) {
        return getFactory().createWebSocketClient(url, configuration);
    }

    private static WebSocketClientFactory getFactory() {
        if (factory == null) {
            synchronized (WebSocketClientFactoryResolver.class) { // double check
                if (factory == null) {
                    factory = resolveClientFactory();
                }
            }
        }
        return factory;
    }

    private static WebSocketClientFactory resolveClientFactory() {
        final Iterator<WebSocketClientFactory> i = ServiceLoader.load(WebSocketClientFactory.class).iterator();
        if (i.hasNext()) {
            return i.next();
        }
        throw new IllegalStateException("No HttpClientFactory present on classpath, cannot create client");
    }
}
