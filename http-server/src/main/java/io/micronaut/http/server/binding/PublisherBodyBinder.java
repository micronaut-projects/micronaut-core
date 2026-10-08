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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a reactive streams {@link Publisher}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class PublisherBodyBinder implements NonBlockingBodyArgumentBinder<Publisher<?>> {

    public static final String MSG_CONVERT_DEBUG = "Cannot convert message for argument [{}] and value: {}";
    private static final Logger LOG = LoggerFactory.getLogger(PublisherBodyBinder.class);
    private static final Argument<Publisher<?>> TYPE = (Argument) Argument.of(Publisher.class);

    private final ServerBodyAnnotationBinder<Object> bodyAnnotationBinder;

    /**
     * @param bodyAnnotationBinder Body annotation binder
     */
    public PublisherBodyBinder(ServerBodyAnnotationBinder<Object> bodyAnnotationBinder) {
        this.bodyAnnotationBinder = bodyAnnotationBinder;
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Publisher<?>> bind(ArgumentConversionContext<Publisher<?>> context, HttpRequest<?> source) {
        ServerHttpRequest<?> server = bodyAnnotationBinder.bodyOf(source);
        if (server != null) {
            ByteBody rootBody = server.byteBody();
            if (rootBody.expectedLength().orElse(-1) == 0) {
                return BindingResult.empty();
            }
            @SuppressWarnings("unchecked")
            Argument<Object> targetType = (Argument<Object>) context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            MediaType mediaType = source.getContentType().orElse(null);
            if (!Publishers.isSingle(context.getArgument().getType()) && !context.getArgument().isSpecifiedSingle() && mediaType != null) {
                // the route reads the elements of its body argument with a reader specialized for them
                Optional<ChunkedMessageBodyReader<Object>> reader = RouteAttributes.getRouteInfo(source)
                    .map(RouteInfo::getMessageBodyReader)
                    .flatMap(PublisherBodyBinder::chunked)
                    .filter(r -> r.isReadable(targetType, mediaType))
                    .or(() -> bodyAnnotationBinder.bodyHandlerRegistry.findReader(targetType, List.of(mediaType))
                        .flatMap(PublisherBodyBinder::chunked));
                if (reader.isPresent()) {
                    Publisher<?> pub = reader.get().readChunked(targetType, mediaType, source.getHeaders(), rootBody.toByteBufferPublisher());
                    return () -> Optional.of(pub);
                }
            }
            // bind a single result
            ExecutionFlow<Object> flow = InternalByteBody.bufferFlow(rootBody)
                .flatMap(bytes -> {
                    return bodyAnnotationBinder.transform(source, server, context.with(targetType), bytes)
                        .map(value -> value.orElseThrow(() -> PublisherBodyBinder.extractError(null, context)));
                });
            Publisher<Object> future = ReactiveExecutionFlow.toPublisher(flow);
            return () -> Optional.of(future);
        }
        return BindingResult.empty();
    }

    @SuppressWarnings("unchecked")
    private static Optional<ChunkedMessageBodyReader<Object>> chunked(MessageBodyReader<?> reader) {
        return reader instanceof ChunkedMessageBodyReader<?> chunked ? Optional.of((ChunkedMessageBodyReader<Object>) chunked) : Optional.empty();
    }

    static RuntimeException extractError(@Nullable Object message, ArgumentConversionContext<?> conversionContext) {
        Optional<ConversionError> lastError = conversionContext.getLastError();
        if (lastError.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(MSG_CONVERT_DEBUG, conversionContext.getArgument(), lastError.get());
            }
            return new ConversionErrorException(conversionContext.getArgument(), lastError.get());
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug(MSG_CONVERT_DEBUG, conversionContext.getArgument(), message);
            }
            return UnsatisfiedRouteException.create(conversionContext.getArgument());
        }
    }
}
