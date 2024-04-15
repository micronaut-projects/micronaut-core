/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.converters.NettyConverters;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bind values annotated with {@link Part}.
 *
 * @param <T> The part type
 * @author Denis Stepanov
 * @since 4.0.0
 */
final class NettyPartUploadAnnotationBinder<T> implements AnnotatedRequestArgumentBinder<Part, T>, RequestArgumentBinder<T> {

    private final ConversionService conversionService;
    private final NettyCompletedFileUploadBinder completedFileUploadBinder;
    private final NettyPublisherPartUploadBinder publisherPartUploadBinder;

    NettyPartUploadAnnotationBinder(ConversionService conversionService,
                                           NettyCompletedFileUploadBinder completedFileUploadBinder,
                                           NettyPublisherPartUploadBinder publisherPartUploadBinder) {
        this.conversionService = conversionService;
        this.completedFileUploadBinder = completedFileUploadBinder;
        this.publisherPartUploadBinder = publisherPartUploadBinder;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        if (!(request instanceof NettyHttpRequest<?> nettyRequest) || !nettyRequest.isFormOrMultipartData()) {
            return BindingResult.unsatisfied();
        }
        if (completedFileUploadBinder.matches(context.getArgument().getType())) {
            return completedFileUploadBinder.bind((ArgumentConversionContext) context, request);
        }
        if (publisherPartUploadBinder.matches(context.getArgument().getType())) {
            return publisherPartUploadBinder.bind((ArgumentConversionContext) context, request);
        }

        Argument<T> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        return bindPart(conversionService, context, nettyRequest, inputName, false);
    }

    @NotNull
    static <T> BindingResult<T> bindPart(ConversionService conversionService, ArgumentConversionContext<T> context, NettyHttpRequest<?> nettyRequest, String inputName, boolean skipClaimed) {
        if (skipClaimed && nettyRequest.formRouteCompleter().isClaimed(inputName)) {
            return BindingResult.unsatisfied();
        }
        CompletableFuture<Optional<T>> completableFuture = Mono.from(nettyRequest.formRouteCompleter().claimFieldsComplete(inputName))
            .map(d -> NettyConverters.refCountAwareConvert(conversionService, d, context))
            .toFuture();

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !completableFuture.isDone();
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }

            @Override
            public Optional<T> getValue() {
                return completableFuture.getNow(Optional.empty());
            }
        };
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }
}
