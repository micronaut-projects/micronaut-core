/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.binders;

import com.typesafe.netty.http.StreamedHttpRequest;
import io.netty.buffer.ByteBufHolder;
import org.particleframework.context.BeanLocator;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.exceptions.ConversionErrorException;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.binding.binders.DefaultBodyAnnotationBinder;
import org.particleframework.http.server.binding.binders.NonBlockingBodyArgumentBinder;
import org.particleframework.http.server.netty.DefaultHttpContentProcessor;
import org.particleframework.http.server.netty.HttpContentProcessor;
import org.particleframework.http.server.netty.HttpContentSubscriberFactory;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.web.router.exceptions.UnsatisfiedRouteException;
import org.particleframework.web.router.qualifier.ConsumesMediaTypeQualifier;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * A {@link org.particleframework.http.annotation.Body} argument binder for a reactive streams {@link Publisher}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class PublisherBodyBinder extends DefaultBodyAnnotationBinder<Publisher> implements NonBlockingBodyArgumentBinder<Publisher> {

    private final BeanLocator beanLocator;
    private final HttpServerConfiguration httpServerConfiguration;

    public PublisherBodyBinder(ConversionService conversionService, BeanLocator beanLocator, HttpServerConfiguration httpServerConfiguration) {
        super(conversionService);
        this.beanLocator = beanLocator;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public Argument<Publisher> argumentType() {
        return Argument.of(Publisher.class);
    }

    @Override
    public BindingResult<Publisher> bind(ArgumentConversionContext<Publisher> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
            if (nativeRequest instanceof StreamedHttpRequest) {
                Optional<MediaType> contentType = source.getContentType();
                HttpContentProcessor<?> processor = contentType.flatMap(type ->
                        beanLocator.findBean(HttpContentSubscriberFactory.class,
                                new ConsumesMediaTypeQualifier<>(type))
                ).map(factory ->
                        factory.build(nettyHttpRequest)
                ).orElse(new DefaultHttpContentProcessor(nettyHttpRequest, httpServerConfiguration));
                return ()-> Optional.of(subscriber -> processor.subscribe(new CompletionAwareSubscriber<Object>() {

                    @Override
                    protected void doOnSubscribe(Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    protected void doOnNext(Object message) {
                        ArgumentConversionContext<?> conversionContext = context.with(context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
                        if(message instanceof ByteBufHolder) {
                            message = ((ByteBufHolder)message).content();
                        }
                        Optional<?> converted = conversionService.convert(message, conversionContext);
                        if(converted.isPresent()) {
                            subscriber.onNext(converted.get());
                        }
                        else {
                            Optional<ConversionError> lastError = conversionContext.getLastError();
                            if(lastError.isPresent()) {
                                subscriber.onError(new ConversionErrorException(context.getArgument(), lastError.get()));
                            }
                            else {
                                subscriber.onError(new UnsatisfiedRouteException(context.getArgument()));
                            }
                        }
                    }

                    @Override
                    protected void doOnError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    protected void doOnComplete() {
                        subscriber.onComplete();
                    }

                }));
            }
        }
        return BindingResult.EMPTY;
    }
}
