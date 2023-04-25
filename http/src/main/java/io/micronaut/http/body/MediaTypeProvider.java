package io.micronaut.http.body;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;

/**
 * Interface for bodies that provide their own media type.
 *
 * @since 4.0.0
 */
public interface MediaTypeProvider {
    /**
     * @return The media type of the object.
     */
    @NonNull
    MediaType getMediaType();
}
