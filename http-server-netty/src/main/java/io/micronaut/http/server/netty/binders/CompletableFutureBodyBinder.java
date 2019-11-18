/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.context.BeanLocator;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.netty.DefaultHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentSubscriberFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
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

    private final BeanLocator beanLocator;
    private final HttpServerConfiguration httpServerConfiguration;

    /**
     * @param beanLocator             The bean locator
     * @param httpServerConfiguration The Http server configuration
     * @param conversionService       The conversion service
     */
    public CompletableFutureBodyBinder(BeanLocator beanLocator, HttpServerConfiguration httpServerConfiguration, ConversionService conversionService) {
        super(conversionService);
        this.beanLocator = beanLocator;
        this.httpServerConfiguration = httpServerConfiguration;
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
                Optional<MediaType> contentType = source.getContentType();
                HttpContentProcessor<?> processor = contentType
                    .flatMap(type -> beanLocator.findBean(HttpContentSubscriberFactory.class, new ConsumesMediaTypeQualifier<>(type)))
                    .map(factory -> factory.build(nettyHttpRequest))
                    .orElse(new DefaultHttpContentProcessor(nettyHttpRequest, httpServerConfiguration));

                processor.subscribe(new CompletionAwareSubscriber<Object>() {
                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    protected void doOnNext(Object message) {
                        nettyHttpRequest.setBody(message);
                        subscription.request(1);
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        future.completeExceptionally(t);
                    }

                    @Override
                    protected void doOnComplete() {
                        Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
                        Optional body = nettyHttpRequest.getBody();
                        if (body.isPresent()) {

                            if (firstTypeParameter.isPresent()) {
                                Argument<?> arg = firstTypeParameter.get();
                                Class targetType = arg.getType();
                                Optional converted = conversionService.convert(body.get(), context.with(arg));
                                if (converted.isPresent()) {
                                    future.complete(converted.get());
                                } else {
                                    future.completeExceptionally(new IllegalArgumentException("Cannot bind JSON to argument type: " + targetType.getName()));
                                }
                            } else {
                                future.complete(body.get());
                            }
                        } else {
                            future.complete(null);
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
