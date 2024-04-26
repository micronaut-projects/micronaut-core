package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.body.InboundByteBody;

/**
 * This interface extends {@link HttpRequest} with methods that are specific to a request received
 * by an HTTP server.
 *
 * @param <B> The body type
 */
@Experimental
public interface ServerHttpRequest<B> extends HttpRequest<B> {
    /**
     * Get the bytes of the body. The body is owned by the request, so the caller should generally
     * not close it or do any primary operations. The body is usually consumed by the argument
     * binder of the controller, e.g. if it has a {@code @Body} argument. If you want to use the
     * body, {@link InboundByteBody#split(InboundByteBody.SplitBackpressureMode)} it first.
     *
     * @return The body bytes of this request
     */
    InboundByteBody byteBody();
}
