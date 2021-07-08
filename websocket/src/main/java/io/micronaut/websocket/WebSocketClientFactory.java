package io.micronaut.websocket;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClientConfiguration;

import java.net.URL;

public interface WebSocketClientFactory {

    /**
     * Create a new {@link WebSocketClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    WebSocketClient createWebSocketClient(@Nullable URL url);

    /**
     * Create a new {@link WebSocketClient} with the specified configuration. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration The client configuration
     * @return The client
     */
    @NonNull
    WebSocketClient createWebSocketClient(@Nullable URL url,  HttpClientConfiguration configuration);

}
