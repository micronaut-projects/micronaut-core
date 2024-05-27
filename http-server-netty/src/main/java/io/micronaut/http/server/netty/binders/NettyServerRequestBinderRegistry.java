/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.MultipartBodyArgumentBinder;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * A Netty request binder registry.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@Singleton
@Order(100) // Prefer default implementation
public final class NettyServerRequestBinderRegistry implements RequestBinderRegistry {

    private final DefaultRequestBinderRegistry internalRequestBinderRegistry;

    public NettyServerRequestBinderRegistry(ConversionService conversionService,
                                            List<RequestArgumentBinder> binders,
                                            BeanLocator beanLocator,
                                            BeanProvider<HttpServerConfiguration> httpServerConfiguration,
                                            @Named(TaskExecutors.BLOCKING)
                                            BeanProvider<ExecutorService> executorService,
                                            MessageBodyHandlerRegistry bodyHandlerRegistry) {

        NettyBodyAnnotationBinder<Object> nettyBodyAnnotationBinder = new NettyBodyAnnotationBinder<>(conversionService, httpServerConfiguration.get(), bodyHandlerRegistry);

        internalRequestBinderRegistry = new DefaultRequestBinderRegistry(conversionService, binders, nettyBodyAnnotationBinder);

        internalRequestBinderRegistry.addArgumentBinder(new NettyCompletableFutureBodyBinder(
            nettyBodyAnnotationBinder));
        internalRequestBinderRegistry.addArgumentBinder(new NettyPublisherBodyBinder(
            nettyBodyAnnotationBinder));
        internalRequestBinderRegistry.addArgumentBinder(new MultipartBodyArgumentBinder(
            beanLocator,
            httpServerConfiguration
        ));
        internalRequestBinderRegistry.addArgumentBinder(new NettyInputStreamBodyBinder());
        NettyStreamingFileUpload.Factory fileUploadFactory = new NettyStreamingFileUpload.Factory(
            httpServerConfiguration.get().getMultipart(),
            executorService.get()
        );
        internalRequestBinderRegistry.addArgumentBinder(new NettyStreamingFileUploadBinder(fileUploadFactory));
        NettyCompletedFileUploadBinder completedFileUploadBinder = new NettyCompletedFileUploadBinder(conversionService);
        internalRequestBinderRegistry.addArgumentBinder(completedFileUploadBinder);
        NettyPublisherPartUploadBinder publisherPartUploadBinder = new NettyPublisherPartUploadBinder(conversionService, fileUploadFactory);
        internalRequestBinderRegistry.addArgumentBinder(publisherPartUploadBinder);
        NettyPartUploadAnnotationBinder<Object> partUploadAnnotationBinder = new NettyPartUploadAnnotationBinder<>(
            conversionService,
            completedFileUploadBinder,
            publisherPartUploadBinder
        );
        internalRequestBinderRegistry.addArgumentBinder(partUploadAnnotationBinder);

        internalRequestBinderRegistry.addUnmatchedRequestArgumentBinder(partUploadAnnotationBinder);
    }

    @Override
    public <T> void addArgumentBinder(ArgumentBinder<T, HttpRequest<?>> binder) {
        internalRequestBinderRegistry.addArgumentBinder(binder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
        return internalRequestBinderRegistry.findArgumentBinder(argument);
    }

}
