package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.body.InboundByteBody;

public interface ServerHttpRequest<B> extends HttpRequest<B> {
    @Experimental
    InboundByteBody byteBody();
}
