package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;

@Experimental
public final class ServerHttpResponseWrapper<B> extends HttpResponseWrapper<B> implements ServerHttpResponse<B> {
    private final CloseableByteBody byteBody;

    private ServerHttpResponseWrapper(HttpResponse<B> delegate, CloseableByteBody byteBody) {
        super(delegate);
        this.byteBody = byteBody;
    }

    public static ServerHttpResponse<?> wrap(HttpResponse<?> delegate, CloseableByteBody byteBody) {
        return new ServerHttpResponseWrapper<>(delegate, byteBody);
    }

    @Override
    public @NonNull ByteBody byteBody() {
        return byteBody;
    }

    @Override
    public void close() {
        byteBody.close();
    }
}
