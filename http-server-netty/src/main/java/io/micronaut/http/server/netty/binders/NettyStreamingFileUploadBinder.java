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
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binds {@link StreamingFileUpload}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class NettyStreamingFileUploadBinder implements TypedRequestArgumentBinder<StreamingFileUpload>, NettyRequestArgumentBinder<StreamingFileUpload> {

    private static final Argument<StreamingFileUpload> STREAMING_FILE_UPLOAD_ARGUMENT = Argument.of(StreamingFileUpload.class);

    private final NettyStreamingFileUpload.Factory fileUploadFactory;

    NettyStreamingFileUploadBinder(NettyStreamingFileUpload.Factory fileUploadFactory) {
        this.fileUploadFactory = fileUploadFactory;
    }

    @Override
    public BindingResult<StreamingFileUpload> bindForNettyRequest(ArgumentConversionContext<StreamingFileUpload> context,
                                                                  NettyHttpRequest<?> request) {

        Argument<StreamingFileUpload> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        CompletableFuture<? extends StreamingFileUpload> completableFuture = Mono.from(
            request.formRouteCompleter().claimFields(inputName, (data, publisher) -> fileUploadFactory.create((FileUpload) data, publisher))).toFuture();

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !completableFuture.isDone();
            }

            @Override
            public Optional<StreamingFileUpload> getValue() {
                return Optional.ofNullable(completableFuture.getNow(null));
            }
        };
    }

    @Override
    public Argument<StreamingFileUpload> argumentType() {
        return STREAMING_FILE_UPLOAD_ARGUMENT;
    }
}
