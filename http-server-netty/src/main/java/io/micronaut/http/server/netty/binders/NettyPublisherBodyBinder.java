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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
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
final class NettyPublisherBodyBinder implements NonBlockingBodyArgumentBinder<Publisher<?>> {

    public static final String MSG_CONVERT_DEBUG = "Cannot convert message for argument [{}] and value: {}";
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Argument<Publisher<?>> TYPE = (Argument) Argument.of(Publisher.class);

    private final NettyBodyAnnotationBinder<Object> nettyBodyAnnotationBinder;

    /**
     * @param nettyBodyAnnotationBinder Body annotation binder
     */
    NettyPublisherBodyBinder(NettyBodyAnnotationBinder<Object> nettyBodyAnnotationBinder) {
        this.nettyBodyAnnotationBinder = nettyBodyAnnotationBinder;
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Publisher<?>> bind(ArgumentConversionContext<Publisher<?>> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nhr) {
            ByteBody rootBody = nhr.byteBody();
            if (rootBody.expectedLength().orElse(-1) == 0) {
                return BindingResult.empty();
            }
            @SuppressWarnings("unchecked")
            Argument<Object> targetType = (Argument<Object>) context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            MediaType mediaType = nhr.getContentType().orElse(null);
            if (!Publishers.isSingle(context.getArgument().getType()) && !context.getArgument().isSpecifiedSingle() && mediaType != null) {
                Optional<MessageBodyReader<Object>> reader = nettyBodyAnnotationBinder.bodyHandlerRegistry.findReader(targetType, List.of(mediaType));
                if (reader.isPresent() && reader.get() instanceof ChunkedMessageBodyReader<Object> piecewise) {
                    Publisher<?> pub = piecewise.readChunked(targetType, mediaType, nhr.getHeaders(), rootBody.toByteBufferPublisher());
                    return () -> Optional.of(pub);
                }
            }
            // bind a single result
            ExecutionFlow<Object> flow = InternalByteBody.bufferFlow(rootBody)
                .map(bytes -> {
                    Optional<Object> value;
                    try {
                        value = nettyBodyAnnotationBinder.transform(nhr, context.with(targetType), bytes);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    return value.orElseThrow(() -> NettyPublisherBodyBinder.extractError(null, context));
                });
            Publisher<Object> future = ReactiveExecutionFlow.toPublisher(flow);
            return () -> Optional.of(future);
        }
        return BindingResult.empty();
    }

    static RuntimeException extractError(Object message, ArgumentConversionContext<?> conversionContext) {
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
