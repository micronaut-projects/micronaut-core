/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

/**
 * A {@link NonBlockingBodyArgumentBinder} that handles {@link CompletableFuture} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class CompletableFutureBodyBinder implements NonBlockingBodyArgumentBinder<CompletableFuture<?>>, StreamedNettyRequestArgumentBinder<CompletableFuture<?>> {

    private static final Argument<CompletableFuture<?>> TYPE = (Argument) Argument.of(CompletableFuture.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;

    /**
     * @param httpContentProcessorResolver The http content processor resolver
     */
    public CompletableFutureBodyBinder(HttpContentProcessorResolver httpContentProcessorResolver) {
        this.httpContentProcessorResolver = httpContentProcessorResolver;
    }

    @NonNull
    @Override
    public List<Class<?>> superTypes() {
        return Arrays.asList(CompletionStage.class, Future.class);
    }

    @Override
    public Argument<CompletableFuture<?>> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<CompletableFuture<?>> bindForStreamedNettyRequest(ArgumentConversionContext<CompletableFuture<?>> context,
                                                                           StreamedHttpRequest streamedHttpRequest,
                                                                           NettyHttpRequest<?> nettyHttpRequest) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        Argument<?> targetType = context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);

        nettyHttpRequest.setUsesHttpContentProcessor();
        HttpContentProcessor processor = httpContentProcessorResolver.resolve(nettyHttpRequest, targetType);

        nettyHttpRequest.readRequestBody(processor).onComplete((httpRequest, throwable) -> {
            if (throwable != null) {
                future.complete(throwable);
            } else {
                Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
                if (firstTypeParameter.isPresent()) {
                    Argument<?> arg = firstTypeParameter.get();
                    Optional<?> converted = nettyHttpRequest.getBody(arg);
                    if (converted.isPresent()) {
                        future.complete(converted.get());
                    } else {
                        future.completeExceptionally(new IllegalArgumentException("Cannot bind body to argument type: " + arg.getType().getName()));
                    }
                } else {
                    future.complete(nettyHttpRequest.getBody().orElse(null));
                }
            }
        });

        return () -> Optional.of(future);
    }
}
