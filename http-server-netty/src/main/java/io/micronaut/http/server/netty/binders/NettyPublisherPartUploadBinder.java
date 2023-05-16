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
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.netty.MicronautHttpData;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Bind publisher annotated {@link io.micronaut.http.annotation.Part}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class NettyPublisherPartUploadBinder implements TypedRequestArgumentBinder<Publisher<?>>, NettyRequestArgumentBinder<Publisher<?>> {

    private static final Argument<Publisher<?>> PUBLISHER_ARGUMENT = (Argument) Argument.of(Publisher.class);
    private static final Argument<PartData> PART_DATA_ARGUMENT = Argument.of(PartData.class);

    private final ConversionService conversionService;
    private final NettyStreamingFileUpload.Factory fileUploadFactory;

    NettyPublisherPartUploadBinder(ConversionService conversionService, NettyStreamingFileUpload.Factory fileUploadFactory) {
        this.conversionService = conversionService;
        this.fileUploadFactory = fileUploadFactory;
    }

    @Override
    public BindingResult<Publisher<?>> bindForNettyRequest(ArgumentConversionContext<Publisher<?>> context,
                                                           NettyHttpRequest<?> request) {
        if (request.getContentType().isEmpty() || !request.isFormOrMultipartData()) {
            return BindingResult.unsatisfied();
        }

        Argument<Publisher<?>> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        Argument<?> contentArgument = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        Class<?> contentTypeClass = contentArgument.getType();

        Flux<?> publisher;
        if (contentTypeClass == StreamingFileUpload.class) {
            publisher = request.formRouteCompleter().claimFields(inputName, (data, flux) -> fileUploadFactory.create((FileUpload) data, flux));
        } else if (contentTypeClass == Publisher.class) {
            Argument<?> nestedType = contentArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            publisher = request.formRouteCompleter()
                .claimFields(inputName, (data, flux) -> flux.mapNotNull(partData -> conversionService.convert(partData, nestedType).orElse(null)));
        } else {
            Flux<? extends MicronautHttpData<?>> raw = request.formRouteCompleter().claimFieldsRaw(inputName);
            Flux<?> mnTypeIfNecessary;
            if (contentTypeClass == PartData.class || ClassUtils.isJavaLangType(contentTypeClass)) {
                mnTypeIfNecessary = raw
                    .mapNotNull(data -> {
                        MicronautHttpData<?>.Chunk chunk = data.pollChunk();
                        if (chunk != null) {
                            return new NettyPartData(() -> {
                                if (data instanceof FileUpload fileUpload) {
                                    return Optional.of(MediaType.of(fileUpload.getContentType()));
                                } else {
                                    return Optional.empty();
                                }
                            }, chunk::claim);
                        } else {
                            return null;
                        }
                    });
            } else {
                mnTypeIfNecessary = raw;
            }
            publisher = mnTypeIfNecessary.mapNotNull(it -> conversionService.convert(it, contentArgument).orElse(null));
        }

        return () -> Optional.of(publisher);
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return PUBLISHER_ARGUMENT;
    }
}
