package io.micronaut.http.server.netty.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.server.netty.multipart.NettyPartData;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class NettyPartDataToObjectConverter implements TypeConverter<NettyPartData, Object> {

    private final ConversionService conversionService;

    protected NettyPartDataToObjectConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<Object> convert(NettyPartData object, Class<Object> targetType, ConversionContext context) {
        try {
            return conversionService.convert(object.getByteBuf(), targetType, context);
        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
