package io.micronaut.http.server.netty.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.HttpData;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class HttpDataToByteArrayConverter implements TypeConverter<HttpData, byte[]> {

    private final ConversionService conversionService;

    protected HttpDataToByteArrayConverter(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<byte[]> convert(HttpData upload, Class<byte[]> targetType, ConversionContext context) {
        try {
            if (!upload.isCompleted()) {
                return Optional.empty();
            }
            ByteBuf byteBuf = upload.getByteBuf();
            return conversionService.convert(byteBuf, targetType, context);
        } catch (Exception e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}

