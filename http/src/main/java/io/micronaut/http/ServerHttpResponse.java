package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.ByteBody;

import java.io.Closeable;

@Experimental
public interface ServerHttpResponse<B> extends HttpResponse<B>, Closeable {
    @NonNull
    ByteBody byteBody();

    @Override
    void close();
}
