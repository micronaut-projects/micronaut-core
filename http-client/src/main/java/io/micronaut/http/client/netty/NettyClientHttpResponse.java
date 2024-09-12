package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Optional;
import java.util.function.Supplier;

@Internal
final class NettyClientHttpResponse implements ByteBodyHttpResponse<Object> {
    final HttpResponse nettyResponse;
    private final CloseableByteBody body;
    private final NettyHttpHeaders headers;
    private final Supplier<MutableConvertibleValues<Object>> attributes = SupplierUtil.memoized(MutableConvertibleValuesMap::new);

    NettyClientHttpResponse(HttpResponse nettyResponse, CloseableByteBody body, ConversionService conversionService) {
        this.nettyResponse = nettyResponse;
        this.body = body;
        this.headers = new NettyHttpHeaders(nettyResponse.headers(), conversionService);
    }

    @Override
    public @NonNull ByteBody byteBody() {
        return body;
    }

    @Override
    public void close() {
        body.close();
    }

    @Override
    public int code() {
        return nettyResponse.status().code();
    }

    @Override
    public String reason() {
        return nettyResponse.status().reasonPhrase();
    }

    @Override
    public @NonNull NettyHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public @NonNull MutableConvertibleValues<Object> getAttributes() {
        return attributes.get();
    }

    @Override
    public @NonNull Optional<Object> getBody() {
        return Optional.empty();
    }
}
