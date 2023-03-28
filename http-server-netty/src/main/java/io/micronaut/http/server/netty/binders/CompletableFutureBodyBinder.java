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
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorAsReactiveProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
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
    implements NonBlockingBodyArgumentBinder<CompletableFuture> {

    private static final Argument<CompletableFuture> TYPE = Argument.of(CompletableFuture.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;

    /**
     * @param httpContentProcessorResolver The http content processor resolver
     * @param conversionService            The conversion service
     */
    public CompletableFutureBodyBinder(HttpContentProcessorResolver httpContentProcessorResolver, ConversionService conversionService) {
        this.httpContentProcessorResolver = httpContentProcessorResolver;
    }

    @NonNull
    @Override
    public List<Class<?>> superTypes() {
        return Arrays.asList(CompletionStage.class, Future.class);
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
            Argument<?> targetType = context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            HttpContentProcessor processor = httpContentProcessorResolver.resolve(nettyHttpRequest, targetType);
            if (nativeRequest instanceof FullHttpRequest fullHttpRequest && fullHttpRequest.content().isReadable()) {
                // we will read the body, retain the request
                fullHttpRequest.retain();
                List<Object> buffer = new ArrayList<>(1);
                try {
                    processor.add(fullHttpRequest, buffer);
                    processor.complete(buffer);
                    for (Object object : buffer) {
                        if (object instanceof ByteBufHolder holder) {
                            nettyHttpRequest.addContent(holder);
                        } else {
                            nettyHttpRequest.setBody(object);
                        }
                        // upstream producer gave us control of the message. release it now, if we still need it,
                        // nettyHttpRequest will have retained it
                        ReferenceCountUtil.release(object);
                    }
                    Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
                    CompletableFuture future;
                    if (firstTypeParameter.isPresent()) {
                        Argument<?> arg = firstTypeParameter.get();
                        Optional converted = nettyHttpRequest.getBody(arg);
                        if (converted.isPresent()) {
                            future = CompletableFuture.completedFuture(converted.get());
                        } else {
                            future = CompletableFuture.failedFuture(new IllegalArgumentException("Cannot bind body to argument type: " + arg.getType().getName()));
                        }
                    } else {
                        future = CompletableFuture.completedFuture(nettyHttpRequest.getBody().orElse(null));
                    }
                    return () -> Optional.of(future);
                } catch (Throwable t) {
                    try {
                        processor.cancel();
                    } catch (Throwable u) {
                        t.addSuppressed(u);
                    }
                    return () -> Optional.of(CompletableFuture.failedFuture(t));
                } finally {
                    for (Object o : buffer) {
                        ReferenceCountUtil.release(o);
                    }
                }
            } else if (nativeRequest instanceof StreamedHttpRequest) {
                CompletableFuture future = new CompletableFuture();
                HttpContentProcessorAsReactiveProcessor.asPublisher(processor, nettyHttpRequest).subscribe(new CompletionAwareSubscriber<Object>() {
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
                        // upstream producer gave us control of the message. release it now, if we still need it,
                        // nettyHttpRequest will have retained it
                        ReferenceCountUtil.release(message);
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
