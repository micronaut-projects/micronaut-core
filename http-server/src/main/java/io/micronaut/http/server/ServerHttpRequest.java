package io.micronaut.http.server;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.InboundByteBody;

public interface ServerHttpRequest<B> extends HttpRequest<B> {
    @Experimental
    InboundByteBody byteBody();
}
