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
import io.micronaut.http.annotation.Part;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.NettyHttpRequest;

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
public class PartUploadAnnotationBinder<T> implements AnnotatedRequestArgumentBinder<Part, T>, StreamedNettyRequestArgumentBinder<T> {

    private final ConversionService conversionService;
    private final CompletedFileUploadBinder completedFileUploadBinder;
    private final PublisherPartUploadBinder publisherPartUploadBinder;

    public PartUploadAnnotationBinder(ConversionService conversionService,
                                      CompletedFileUploadBinder completedFileUploadBinder,
                                      PublisherPartUploadBinder publisherPartUploadBinder) {
        this.conversionService = conversionService;
        this.completedFileUploadBinder = completedFileUploadBinder;
        this.publisherPartUploadBinder = publisherPartUploadBinder;
    }

    @Override
    public BindingResult<T> bindForStreamedNettyRequest(ArgumentConversionContext<T> context,
                                                        StreamedHttpRequest streamedHttpRequest,
                                                        NettyHttpRequest<?> request) {
        if (request.getContentType().isEmpty() || !request.isFormOrMultipartData()) {
            return BindingResult.unsatisfied();
        }
        if (completedFileUploadBinder.matches(context.getArgument().getType())) {
            return completedFileUploadBinder.bind((ArgumentConversionContext) context, request);
        }
        if (publisherPartUploadBinder.matches(context.getArgument().getType())) {
            return publisherPartUploadBinder.bind((ArgumentConversionContext) context, request);
        }

        CompletableFuture<T> completableFuture = new CompletableFuture<>();

        Argument<T> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        request.observeFileUpload()
            .filter(data -> data.getName().equals(inputName) && data.isCompleted())
            .take(1)
            .doOnNext(data -> {
                if (data.refCnt() > 0) {
                    completableFuture.complete(conversionService.convert(data, argument.getType(), context).orElse(null));
                } else {
                    completableFuture.complete(null);
                }
            })
            .subscribe();

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
                return Optional.ofNullable(completableFuture.getNow(null));
            }
        };
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }
}
