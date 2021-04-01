package io.micronaut.http.server.netty.types.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.netty.types.NettyCustomizableResponseTypeHandler;
import io.micronaut.http.server.types.CustomizableResponseTypeException;
import io.micronaut.scheduling.TaskExecutors;
import io.netty.channel.ChannelHandlerContext;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Singleton
@Internal
class StreamTypeHandler implements NettyCustomizableResponseTypeHandler<Object> {

    private static final Class<?>[] SUPPORTED_TYPES = new Class[]{NettyStreamedCustomizableResponseType.class, InputStream.class};
    private final ExecutorService executorService;

    StreamTypeHandler(@Named(TaskExecutors.IO) ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void handle(Object object, HttpRequest<?> request, MutableHttpResponse<?> response, ChannelHandlerContext context) {
        NettyStreamedCustomizableResponseType type;

        if (object instanceof InputStream) {
            type = new NettyStreamedCustomizableResponseType() {
                @Override
                public InputStream getInputStream() {
                    return (InputStream) object;
                }

                @Override
                public Executor getExecutor() {
                    return executorService;
                }
            };
        } else if (object instanceof NettyStreamedCustomizableResponseType) {
            type = (NettyStreamedCustomizableResponseType) object;
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

    @Override
    public int getOrder() {
        return 100;
    }
}
