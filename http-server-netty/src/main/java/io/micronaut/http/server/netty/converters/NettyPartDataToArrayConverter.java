package io.micronaut.http.server.netty.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.server.netty.multipart.NettyPartData;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class NettyPartDataToArrayConverter implements TypeConverter<NettyPartData, byte[]> {

    private final ConversionService conversionService;

    protected NettyPartDataToArrayConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<byte[]> convert(NettyPartData object, Class<byte[]> targetType, ConversionContext context) {
        try {
            return conversionService.convert(object.getByteBuf(), targetType, context);
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
