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
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FileUpload;
import io.micronaut.http.server.netty.NettyHttpRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binds {@link CompletedFileUpload}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class NettyCompletedFileUploadBinder implements TypedRequestArgumentBinder<CompletedFileUpload>, NettyRequestArgumentBinder<CompletedFileUpload> {

    private static final Argument<CompletedFileUpload> STREAMING_FILE_UPLOAD_ARGUMENT = Argument.of(CompletedFileUpload.class);

    private final ConversionService conversionService;

    NettyCompletedFileUploadBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public List<Class<?>> superTypes() {
        return List.of(FileUpload.class);
    }

    @Override
    public BindingResult<CompletedFileUpload> bindForNettyRequest(ArgumentConversionContext<CompletedFileUpload> context,
                                                                  NettyHttpRequest<?> request) {
        if (request.getContentType().isEmpty() || !request.isFormOrMultipartData()) {
            return BindingResult.unsatisfied();
        }

        Argument<CompletedFileUpload> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        CompletableFuture<CompletedFileUpload> completableFuture = Mono.from(request.formRouteCompleter().claimFieldsComplete(inputName))
            .map(d -> conversionService.convertRequired(d, CompletedFileUpload.class))
            .toFuture();

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !completableFuture.isDone();
            }

            @Override
            public Optional<CompletedFileUpload> getValue() {
                return Optional.ofNullable(completableFuture.getNow(null));
            }
        };
    }

    @Override
    public Argument<CompletedFileUpload> argumentType() {
        return STREAMING_FILE_UPLOAD_ARGUMENT;
    }
}
