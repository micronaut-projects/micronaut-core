package io.micronaut.http.client;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Interface for managing the construction and lifecycle of instances of {@link RawHttpClient} clients.
 *
 * @author Jonas Konrad
 * @since 4.7.0
 */
@Experimental
public interface RawHttpClientRegistry {
    /**
     * Return the client for the client ID and path.
     *
     * @param httpVersion The HTTP version
     * @param clientId    The client ID
     * @param path        The path (Optional)
     * @return The client
     */
    @NonNull
    RawHttpClient getRawClient(@NonNull HttpVersionSelection httpVersion, @NonNull String clientId, @Nullable String path);
}
