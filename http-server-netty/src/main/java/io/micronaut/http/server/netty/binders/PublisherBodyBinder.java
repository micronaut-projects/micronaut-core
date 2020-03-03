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
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.netty.*;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.EmptyByteBuf;
import io.netty.util.ReferenceCounted;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a reactive streams {@link Publisher}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Internal
public class PublisherBodyBinder extends DefaultBodyAnnotationBinder<Publisher> implements NonBlockingBodyArgumentBinder<Publisher> {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Argument<Publisher> TYPE = Argument.of(Publisher.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;

    /**
     * @param conversionService            The conversion service
     * @param httpContentProcessorResolver The http content processor resolver
     */
    public PublisherBodyBinder(ConversionService conversionService,
                               HttpContentProcessorResolver httpContentProcessorResolver) {
        super(conversionService);
        this.httpContentProcessorResolver = httpContentProcessorResolver;
    }

    @Override
    public Argument<Publisher> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Publisher> bind(ArgumentConversionContext<Publisher> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
            if (nativeRequest instanceof StreamedHttpRequest) {
                Argument<?> targetType = context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);

                HttpContentProcessor<?> processor = httpContentProcessorResolver.resolve(nettyHttpRequest, targetType);

                //noinspection unchecked
                return () -> Optional.of(subscriber -> processor.subscribe(new TypedSubscriber<Object>((Argument) context.getArgument()) {

                    Subscription s;

                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        this.s = subscription;
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    protected void doOnNext(Object message) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Server received streaming message for argument [{}]: {}", context.getArgument(), message);
                        }
                        ArgumentConversionContext<?> conversionContext = context.with(targetType);
                        if (message instanceof ByteBufHolder) {
                            message = ((ByteBufHolder) message).content();
                            if (message instanceof EmptyByteBuf) {
                                return;
                            }
                        }

                        Optional<?> converted = conversionService.convert(message, conversionContext);

                        if (converted.isPresent()) {
                            subscriber.onNext(converted.get());
                        } else {

                            try {
                                Optional<ConversionError> lastError = conversionContext.getLastError();
                                if (lastError.isPresent()) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Cannot convert message for argument [" + context.getArgument() + "] and value: " + message, lastError.get());
                                    }
                                    subscriber.onError(new ConversionErrorException(context.getArgument(), lastError.get()));
                                } else {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Cannot convert message for argument [{}] and value: {}", context.getArgument(), message);
                                    }
                                    subscriber.onError(UnsatisfiedRouteException.create(context.getArgument()));
                                }
                            } finally {
                                s.cancel();
                            }
                        }

                        if (message instanceof ReferenceCounted) {
                            ((ReferenceCounted) message).release();
                        }
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Server received error for argument [" + context.getArgument() + "]: " + t.getMessage(), t);
                        }
                        try {
                            subscriber.onError(t);
                        } finally {
                            s.cancel();
                        }
                    }

                    @Override
                    protected void doOnComplete() {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Done receiving messages for argument: {}", context.getArgument());
                        }
                        subscriber.onComplete();
                    }

                }));
            }
        }
        return BindingResult.EMPTY;
    }
}
