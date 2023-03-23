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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

import java.util.Optional;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a reactive streams {@link Publisher}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class PublisherBodyBinder implements NonBlockingBodyArgumentBinder<Publisher<?>>,
    StreamedNettyRequestArgumentBinder<Publisher<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Argument<Publisher<?>> TYPE = (Argument) Argument.of(Publisher.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;

    /**
     * @param httpContentProcessorResolver The http content processor resolver
     */
    public PublisherBodyBinder(HttpContentProcessorResolver httpContentProcessorResolver) {
        this.httpContentProcessorResolver = httpContentProcessorResolver;
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Publisher<?>> bindForStreamedNettyRequest(ArgumentConversionContext<Publisher<?>> context,
                                                                   StreamedHttpRequest streamedHttpRequest,
                                                                   NettyHttpRequest<?> nettyHttpRequest) {
        Argument<?> targetType = context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);

        nettyHttpRequest.setUsesHttpContentProcessor();
        HttpContentProcessor processor = httpContentProcessorResolver.resolve(nettyHttpRequest, targetType);

        Sinks.One<Object> sink = Sinks.one();

        nettyHttpRequest.readRequestBody(processor).onComplete((httpRequest, throwable) -> {
            if (throwable != null) {
                sink.tryEmitError(throwable);
            } else {
                Optional<Argument<?>> firstTypeParameter = context.getFirstTypeVariable();
                if (firstTypeParameter.isPresent()) {
                    Argument<?> arg = firstTypeParameter.get();
                    Optional<?> converted = nettyHttpRequest.getBody(arg);
                    if (converted.isPresent()) {
                        sink.tryEmitValue(converted.get());
                    } else {
                        sink.tryEmitError(new IllegalArgumentException("Cannot bind body to argument type: " + arg.getType().getName()));
                    }
                } else {
                    sink.tryEmitValue(nettyHttpRequest.getBody().orElse(null));
                }
            }
        });

        return () -> Optional.of(sink.asMono());
    }

}
