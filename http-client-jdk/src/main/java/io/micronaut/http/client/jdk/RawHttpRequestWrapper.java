package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyHttpRequestBuilder;
import io.netty.handler.codec.http.HttpRequest;

import java.io.Closeable;
import java.io.IOException;

/**
 * This is a combination of a {@link HttpRequest} with a {@link ByteBody}. It implements
 * {@link MutableHttpRequest} so that it can be used unchanged in the client,
 * {@link NettyHttpRequestBuilder} so that the bytes are
 *
 * @param <B> The body type, mostly unused
 * @since 4.8.0
 */
@Internal
final class RawHttpRequestWrapper<B> extends MutableHttpRequestWrapper<B> implements MutableHttpRequest<B>, ServerHttpRequest<B>, Closeable {
    private final CloseableByteBody byteBody;

    public RawHttpRequestWrapper(ConversionService conversionService, MutableHttpRequest<B> delegate, CloseableByteBody byteBody) {
        super(conversionService, delegate);
        this.byteBody = byteBody;
    }

    @Override
    public @NonNull ByteBody byteBody() {
        return byteBody;
    }

    @Override
    public <T> MutableHttpRequest<T> body(T body) {
        throw new UnsupportedOperationException("Changing the body of raw requests is currently not supported");
    }

    @Override
    public void close() throws IOException {
        byteBody.close();
    }
}
