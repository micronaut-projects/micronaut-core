package io.micronaut.http.server.netty.types.stream;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.types.CustomizableResponseTypeException;
import io.netty.channel.ChannelHandlerContext;

import javax.inject.Singleton;
import java.io.InputStream;
import java.util.Arrays;

@Singleton
public class StreamTypeHandler implements NettyCustomizableResponseTypeHandler<Object> {

    private static final Class<?>[] SUPPORTED_TYPES = new Class[]{StreamedCustomizableResponseType.class, InputStream.class};

    @Override
    public void handle(Object object, HttpRequest<?> request, MutableHttpResponse<?> response, ChannelHandlerContext context) {
        StreamedCustomizableResponseType type;

        if (object instanceof InputStream) {
            type = () -> (InputStream) object;
        } else if (object instanceof StreamedCustomizableResponseType) {
            type = (StreamedCustomizableResponseType) object;
        } else {
            throw new CustomizableResponseTypeException("StreamTypeHandler only supports InputStream or StreamedCustomizableResponseType types");
        }

        type.process(response);
        type.write(request, response, context);
        context.read();
    }

    @Override
    public boolean supports(Class<?> type) {
        return Arrays.stream(SUPPORTED_TYPES)
                .anyMatch((aClass -> aClass.isAssignableFrom(type)));
    }
}
