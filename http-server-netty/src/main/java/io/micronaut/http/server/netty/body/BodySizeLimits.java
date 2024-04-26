package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;

@Internal
public record BodySizeLimits(long maxBodySize, long maxBufferSize) {
    public static final BodySizeLimits UNLIMITED = new BodySizeLimits(Long.MAX_VALUE, Integer.MAX_VALUE);
}
