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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.netty.MicronautHttpData;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bind publisher annotated {@link io.micronaut.http.annotation.Part}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class PublisherPartUploadBinder implements TypedRequestArgumentBinder<Publisher<?>>, NettyRequestArgumentBinder<Publisher<?>> {

    private static final Argument<Publisher<?>> PUBLISHER_ARGUMENT = (Argument) Argument.of(Publisher.class);
    private static final Argument<PartData> PART_DATA_ARGUMENT = Argument.of(PartData.class);

    private final ConversionService conversionService;
    private final NettyStreamingFileUpload.Factory fileUploadFactory;

    public PublisherPartUploadBinder(ConversionService conversionService, NettyStreamingFileUpload.Factory fileUploadFactory) {
        this.conversionService = conversionService;
        this.fileUploadFactory = fileUploadFactory;
    }

    @Override
    public BindingResult<Publisher<?>> bindForNettyRequest(ArgumentConversionContext<Publisher<?>> context,
                                                           NettyHttpRequest<?> request) {
        if (request.getContentType().isEmpty() || !request.isFormOrMultipartData()) {
            return BindingResult.unsatisfied();
        }

        CompletableFuture<Publisher<?>> completableFuture = new CompletableFuture<>();

        Argument<Publisher<?>> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        Argument<?> contentArgument = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        Class<?> contentTypeClass = contentArgument.getType();

        Flux<?> publisher;
        Flux<MicronautHttpData<?>> cachedStream = request.observeFileUploadWithBackPressure().filter(data -> data.getName().equals(inputName))
            .replay(1)
            .autoConnect();
        if (contentTypeClass.equals(StreamingFileUpload.class)) {
            publisher = cachedStream
                .filter(data -> data instanceof io.netty.handler.codec.http.multipart.FileUpload)
                .distinct()
                .map(data -> {
                    data.retain();
                    return fileUploadFactory.create(
                        (io.netty.handler.codec.http.multipart.FileUpload) data,
                        chunkedProcessing(
                            PART_DATA_ARGUMENT,
                            cachedStream.filter(d -> d == data).doOnComplete(data::release)
                        )
                    );
                });
        } else if (contentTypeClass.equals(Publisher.class)) {
            publisher = cachedStream
                .filter(data -> data instanceof io.netty.handler.codec.http.multipart.FileUpload)
                .map(data -> {
                    data.retain();
                    return chunkedProcessing(
                        contentArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT),
                        cachedStream.filter(d -> d == data).doOnComplete(data::release)
                    );
                });
        } else if (PartData.class.equals(contentTypeClass) || ClassUtils.isJavaLangType(contentTypeClass)) {
            publisher = chunkedProcessing(contentArgument, cachedStream);
        } else {
            publisher = cachedStream
                .flatMap(data -> {
                    Optional<?> convert = conversionService.convert(data, contentArgument);
                    if (convert.isPresent()) {
                        data.retain();
                        return Flux.just(convert.get()).doOnComplete(data::release);
                    }
                    return Flux.empty();
                });
        }

        // TODO: We should consider setting the publisher directly without waiting for the first data

        request.observeFileUpload()
            .filter(data -> data.getName().equals(inputName))
            .take(1)
            .doOnNext(data -> completableFuture.complete(publisher)).subscribe();

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !completableFuture.isDone();
            }

            @Override
            public Optional<Publisher<?>> getValue() {
                return Optional.ofNullable(completableFuture.getNow(null));
            }
        };
    }

    private <T> Flux<T> chunkedProcessing(Argument<T> contentArgument, Flux<MicronautHttpData<?>> cachedStream) {
        return cachedStream
            .flatMap(thisData -> {
                MicronautHttpData<?>.Chunk chunk = thisData.pollChunk();
                if (chunk != null) {
                    NettyPartData part = new NettyPartData(() -> {
                        if (thisData instanceof io.netty.handler.codec.http.multipart.FileUpload fu) {
                            return Optional.of(MediaType.of(fu.getContentType()));
                        } else {
                            return Optional.empty();
                        }
                    }, chunk::claim);
                    Optional<T> convert = conversionService.convert(part, contentArgument);
                    if (convert.isPresent()) {
                        return Mono.just(convert.get());
                    }
                }
                return Mono.empty();
            });
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return PUBLISHER_ARGUMENT;
    }
}
