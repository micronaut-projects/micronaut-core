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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.body.ByteBody;
import io.micronaut.http.server.netty.body.ImmediateByteBody;
import io.micronaut.http.server.netty.body.ImmediateSingleObjectBody;

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
public class CompletableFutureBodyBinder
    implements NonBlockingBodyArgumentBinder<CompletableFuture<?>> {

    private static final Argument<CompletableFuture<?>> TYPE = (Argument) Argument.of(CompletableFuture.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final ConversionService conversionService;
    private final BeanProvider<HttpServerConfiguration> httpServerConfiguration;

    /**
     * @param httpContentProcessorResolver The http content processor resolver
     * @param conversionService            The conversion service
     * @param httpServerConfiguration      The server configuration
     */
    public CompletableFutureBodyBinder(HttpContentProcessorResolver httpContentProcessorResolver, ConversionService conversionService, BeanProvider<HttpServerConfiguration> httpServerConfiguration) {
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.conversionService = conversionService;
        this.httpServerConfiguration = httpServerConfiguration;
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
    public BindingResult<CompletableFuture> bind(ArgumentConversionContext<CompletableFuture> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest nhr) {
            ByteBody rootBody = nhr.rootBody();
            if (rootBody instanceof ImmediateByteBody immediate && immediate.empty()) {
                return BindingResult.EMPTY;
            }

            Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
            Argument<?> targetType = firstTypeParameter.orElse(Argument.OBJECT_ARGUMENT);
            try {
                ExecutionFlow<ImmediateSingleObjectBody> retFlow = rootBody
                    .buffer(nhr.getChannelHandlerContext().alloc())
                    .map(bytes -> {
                        try {
                            return bytes.processMulti(httpContentProcessorResolver.resolve(nhr, targetType))
                                .single(httpServerConfiguration.get().getDefaultCharset(), nhr.getChannelHandlerContext().alloc());
                        } catch (RuntimeException e) {
                            throw e;
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    });
                CompletableFuture<Object> future = retFlow.map(immediateSingleObjectBody -> {
                    Object claimed = immediateSingleObjectBody.claimForExternal();
                    if (firstTypeParameter.isPresent()) {
                        return PublisherBodyBinder.convertAndRelease(conversionService, context.with(targetType), claimed);
                    } else {
                        return claimed;
                    }
                }).toCompletableFuture();
                return () -> Optional.of(future);
            } catch (Throwable e) {
                return () -> Optional.of(CompletableFuture.failedFuture(e));
            }
        } else {
            return BindingResult.EMPTY;
        }
    }
}
