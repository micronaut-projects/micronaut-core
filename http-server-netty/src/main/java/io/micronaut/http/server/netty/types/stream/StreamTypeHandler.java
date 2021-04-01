/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * Responsible for writing streams out to the response in Netty.
 *
 * @author James Kleeh
 * @since 2.5.0
 */
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
