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
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.multipart.HttpData;
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.exceptions.ConversionErrorException;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Part;
import org.particleframework.http.server.binding.binders.AnnotatedRequestArgumentBinder;
import org.particleframework.http.server.netty.FormDataHttpContentProcessor;
import org.particleframework.http.server.netty.HttpContentProcessor;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.http.server.netty.configuration.NettyHttpServerConfiguration;
import org.particleframework.web.router.exceptions.UnsatisfiedRouteException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * Binds a Reactive streams {@link Publisher} to a {@link Part} argument
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class PublisherPartBinder implements AnnotatedRequestArgumentBinder<Part,Publisher>{
    private final NettyHttpServerConfiguration httpServerConfiguration;
    private final ConversionService<?> conversionService;

    public PublisherPartBinder(NettyHttpServerConfiguration httpServerConfiguration, ConversionService<?> conversionService) {
        this.httpServerConfiguration = httpServerConfiguration;
        this.conversionService = conversionService;
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Publisher> bind(ArgumentConversionContext<Publisher> context, HttpRequest source) {
        if(MediaType.MULTIPART_FORM_DATA_TYPE.equals(source.getContentType())) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            Argument<Publisher> argument = context.getArgument();
            String argumentName = argument.getName();
            String expectedInputName = context.findAnnotation(Part.class).map(ann -> StringUtils.isNotEmpty(ann.value()) ? ann.value() : argumentName).orElse(argumentName);
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
            final ArgumentConversionContext<?> typeContext = context.with(context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
            if (nativeRequest instanceof StreamedHttpRequest) {
                return Optional.of(subscriber -> {
                    Subscriber<HttpData> contentSubscriber = new CompletionAwareSubscriber<HttpData>() {
                        HttpData currentPartial = null;
                        CompositeByteBuf partialData = null;

                        @Override
                        protected void doOnSubscribe(Subscription subscription) {
                            subscriber.onSubscribe(subscription);
                        }

                        @Override
                        protected void doOnNext(HttpData data) {
                            String partName = data.getName();
                            if(partName.equals(expectedInputName) ) {

                                Optional<?> converted = conversionService.convert(data, typeContext);
                                if(converted.isPresent()) {
                                    subscriber.onNext(converted.get());
                                }
                                else {
                                    if(!data.isCompleted()) {
                                        Optional<ConversionError> lastError = typeContext.getLastError();
                                        // if there was an error then this is not partial data
                                        if( lastError.isPresent() ) {
                                            onError(new ConversionErrorException(argument, lastError.get()));
                                        }
                                        else {
                                            try {
                                                currentPartial = data;
                                                getPartialData().addComponent(data.getByteBuf());
                                            } catch (IOException e) {
                                                onError(e);
                                            }
                                        }
                                    }
                                    else {
                                        Optional<ConversionError> lastError = typeContext.getLastError();
                                        if( lastError.isPresent() ) {
                                            onError(new ConversionErrorException(argument, lastError.get()));
                                        }
                                        else {
                                            onError(new UnsatisfiedRouteException(argument));
                                        }
                                    }
                                }
                            }
                            else {
                                // not the data we want so keep going
                                subscription.request(1);
                            }
                            if(!data.isCompleted()) {
                                currentPartial = data;
                                subscription.request(1);
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

                        CompositeByteBuf getPartialData() {
                            if(partialData == null)
                                partialData = nettyHttpRequest.getChannelHandlerContext().alloc().compositeBuffer();
                            return partialData;
                        }
                    };
                    HttpContentProcessor processor = new FormDataHttpContentProcessor(nettyHttpRequest, httpServerConfiguration);
                    processor.subscribe(contentSubscriber);
                });
            }
        }
        return Optional.empty();
    }
}
