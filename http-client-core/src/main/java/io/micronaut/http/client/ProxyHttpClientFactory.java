package io.micronaut.http.client;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.URL;

public interface ProxyHttpClientFactory {

    /**
     * Create a new {@link ProxyHttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    ProxyHttpClient createProxyClient(@Nullable URL url);

    /**
     * Create a new {@link ProxyHttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url           The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    @NonNull
    ProxyHttpClient createProxyClient(@Nullable URL url, HttpClientConfiguration configuration);

}
