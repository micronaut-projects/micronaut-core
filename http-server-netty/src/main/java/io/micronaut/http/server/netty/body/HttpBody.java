package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * <p>Base type for a representation of an HTTP request body.</p>
 * <p>Exactly one HttpBody holds control over a request body at a time. When a transformation of
 * the body is performed, e.g. multipart processing, the new HttpBody takes control and the old one
 * becomes invalid. The new body will be available via {@link #next()}.</p>
 */
@Internal
public interface HttpBody {
    /**
     * Release this body and any downstream representations.
     */
    void release();

    /**
     * Get the next representation this body was transformed into, if any.
     *
     * @return The next representation, or {@code null} if this body has not been transformed
     */
    @Nullable
    HttpBody next();
}
