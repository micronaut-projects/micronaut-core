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
package io.micronaut.http.server.netty.binders;

import com.typesafe.netty.http.StreamedHttpRequest;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.server.binding.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.ChunkedFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.server.binding.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.server.netty.FormDataHttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.multipart.ChunkedFileUpload;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Binds a Reactive streams {@link Publisher} to a {@link Part} argument
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class PublisherPartBinder implements AnnotatedRequestArgumentBinder<Part,Publisher> {
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
    public BindingResult<Publisher> bind(ArgumentConversionContext<Publisher> context, HttpRequest<?> source) {
        Optional<MediaType> contentType = source.getContentType();
        if(contentType.isPresent() && MediaType.MULTIPART_FORM_DATA_TYPE.equals(contentType.get())) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            Argument<Publisher> argument = context.getArgument();
            String argumentName = argument.getName();
            String expectedInputName = context.findAnnotation(Part.class).map(ann -> StringUtils.isNotEmpty(ann.value()) ? ann.value() : argumentName).orElse(argumentName);
            io.netty.handler.codec.http.HttpRequest nativeRequest = nettyHttpRequest.getNativeRequest();
            final ArgumentConversionContext<?> typeContext = context.with(context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
            if (nativeRequest instanceof StreamedHttpRequest) {
                return ()-> Optional.of(subscriber -> {
                    Subscriber<HttpData> contentSubscriber = new CompletionAwareSubscriber<HttpData>() {
                        int position = 0;
                        String partName;
                        @Override
                        protected void doOnSubscribe(Subscription subscription) {
                            subscriber.onSubscribe(subscription);
                        }

                        @Override
                        protected void doOnNext(HttpData data) {
                            String name = data.getName();
                            if(partName == null || !partName.equals(name)) {
                                // reset the position
                                position = 0;
                            }
                            partName = name;
                            if(partName.equals(expectedInputName) ) {

                                if(data instanceof FileUpload) {
                                    FileUpload upload = (FileUpload) data;
                                    position += upload.length();
                                    data = new ChunkedFileUpload(position, upload);
                                }

                                Optional<?> converted = conversionService.convert(data, typeContext);
                                if(converted.isPresent()) {
                                    subscriber.onNext(converted.get());
                                }
                                else {
                                    if(data.isCompleted()) {
                                        Optional<ConversionError> lastError = typeContext.getLastError();
                                        if( lastError.isPresent() ) {
                                            onError(new ConversionErrorException(argument, lastError.get()));
                                        }
                                        else {
                                            onError(new UnsatisfiedRouteException(argument));
                                        }
                                    }
                                    else {
                                        // not enough data so keep going
                                        subscription.request(1);
                                    }
                                }
                            }
                            else {
                                // not the data we want so keep going
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

                    };
                    HttpContentProcessor processor = new FormDataHttpContentProcessor(nettyHttpRequest, httpServerConfiguration);
                    processor.subscribe(contentSubscriber);
                });
            }
        }
        return BindingResult.EMPTY;
    }
}
