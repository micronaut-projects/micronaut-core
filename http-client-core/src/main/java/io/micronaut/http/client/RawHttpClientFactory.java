package io.micronaut.http.client;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.URI;

/**
 * Factory for creating {@link RawHttpClient}s without an application context.
 *
 * @since 4.7.0
 * @author Jonas Konrad
 */
@Experimental
public interface RawHttpClientFactory {

    /**
     * Create a new {@link RawHttpClient}. Note that this method should only be used outside the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    RawHttpClient createRawClient(@Nullable URI url);

    /**
     * Create a new {@link RawHttpClient} with the specified configuration. Note that this method should only be used
     * outside the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url           The base URL
     * @param configuration the client configuration
     * @return The client
     */
    @NonNull
    RawHttpClient createRawClient(@Nullable URI url, @NonNull HttpClientConfiguration configuration);
}
