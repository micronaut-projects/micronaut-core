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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.body.InboundByteBody;
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
final class NettyCompletableFutureBodyBinder
    implements NonBlockingBodyArgumentBinder<CompletableFuture<?>> {

    private static final Argument<CompletableFuture<?>> TYPE = (Argument) Argument.of(CompletableFuture.class);

    private final NettyBodyAnnotationBinder<Object> nettyBodyAnnotationBinder;

    /**
     * @param nettyBodyAnnotationBinder The body binder
     */
    NettyCompletableFutureBodyBinder(NettyBodyAnnotationBinder<Object> nettyBodyAnnotationBinder) {
        this.nettyBodyAnnotationBinder = nettyBodyAnnotationBinder;
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
    public BindingResult<CompletableFuture<?>> bind(ArgumentConversionContext<CompletableFuture<?>> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nhr) {
            InboundByteBody rootBody = nhr.byteBody();
            if (rootBody.expectedLength().orElse(-1) == 0) {
                return BindingResult.empty();
            }

            Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
            Argument<?> targetType = firstTypeParameter.orElse(Argument.OBJECT_ARGUMENT);
            CompletableFuture<Object> future = rootBody
                .buffer()
                .map(bytes -> {
                    Optional<Object> value;
                    try {
                        //noinspection unchecked
                        value = nettyBodyAnnotationBinder.transform(nhr, (ArgumentConversionContext<Object>) context.with(targetType), bytes);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    return value.orElseThrow(() -> NettyPublisherBodyBinder.extractError(null, context));
                }).toCompletableFuture();
            return () -> Optional.of(future);
        } else {
            return BindingResult.empty();
        }
    }
}
