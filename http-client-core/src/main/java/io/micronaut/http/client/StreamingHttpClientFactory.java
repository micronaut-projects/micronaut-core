package io.micronaut.http.client;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.net.URL;

public interface StreamingHttpClientFactory {

    /**
     * Create a new {@link StreamingHttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    StreamingHttpClient createStreamingClient(@Nullable URL url);

    /**
     * Create a new {@link StreamingHttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration The client configuration
     * @return The client
     * @since 2.2.0
     */
    @NonNull
    StreamingHttpClient createStreamingClient(@Nullable URL url, HttpClientConfiguration configuration);

}
