/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.buffer.ByteBufHolder;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link NonBlockingBodyArgumentBinder} that handles {@link CompletableFuture} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Internal
public class CompletableFutureBodyBinder extends DefaultBodyAnnotationBinder<CompletableFuture>
    implements NonBlockingBodyArgumentBinder<CompletableFuture> {

    private static final Argument<CompletableFuture> TYPE = Argument.of(CompletableFuture.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;

    /**
     * @param httpContentProcessorResolver The http content processor resolver
     * @param conversionService            The conversion service
     */
    public CompletableFutureBodyBinder(HttpContentProcessorResolver httpContentProcessorResolver, ConversionService conversionService) {
        super(conversionService);
        this.httpContentProcessorResolver = httpContentProcessorResolver;
    }

    @Override
    public Argument<CompletableFuture> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<CompletableFuture> bind(ArgumentConversionContext<CompletableFuture> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            io.netty.handler.codec.http.HttpRequest nativeRequest = ((NettyHttpRequest) source).getNativeRequest();
            if (nativeRequest instanceof StreamedHttpRequest) {

                CompletableFuture future = new CompletableFuture();
                Argument<?> targetType = context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);

                HttpContentProcessor<?> processor = httpContentProcessorResolver.resolve(nettyHttpRequest, targetType);

                processor.subscribe(new CompletionAwareSubscriber<Object>() {
                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    protected void doOnNext(Object message) {
                        if (message instanceof ByteBufHolder) {
                            nettyHttpRequest.addContent((ByteBufHolder) message);
                        } else {
                            nettyHttpRequest.setBody(message);
                        }
                        subscription.request(1);
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        future.completeExceptionally(t);
                    }

                    @Override
                    protected void doOnComplete() {
                        Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
                        if (firstTypeParameter.isPresent()) {
                            Argument<?> arg = firstTypeParameter.get();
                            Optional converted = nettyHttpRequest.getBody(arg);
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
            } else {
                return BindingResult.EMPTY;
            }
        } else {
            return BindingResult.EMPTY;
        }
    }
}
