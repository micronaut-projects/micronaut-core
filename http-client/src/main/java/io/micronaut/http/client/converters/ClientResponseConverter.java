package io.micronaut.http.client.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.FullNettyClientHttpResponse;
import io.micronaut.http.netty.NettyHttpResponse;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Converts a response from the http client to a response processable
 * by the http server.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class ClientResponseConverter implements TypeConverter<FullNettyClientHttpResponse, NettyHttpResponse> {

    private final ConversionService conversionService;

    protected ClientResponseConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<NettyHttpResponse> convert(FullNettyClientHttpResponse object, Class<NettyHttpResponse> targetType, ConversionContext context) {
        return Optional.of(new NettyHttpResponse(object.getNativeResponse(), conversionService).body(object.getBody()));
    }
}
