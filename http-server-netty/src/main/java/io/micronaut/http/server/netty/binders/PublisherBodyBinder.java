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
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.server.netty.body.ImmediateByteBody;
import io.micronaut.http.server.netty.converters.NettyConverters;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * A {@link io.micronaut.http.annotation.Body} argument binder for a reactive streams {@link Publisher}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class PublisherBodyBinder implements NonBlockingBodyArgumentBinder<Publisher<?>> {

    public static final String MSG_CONVERT_DEBUG = "Cannot convert message for argument [{}] and value: {}";
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);
    private static final Argument<Publisher<?>> TYPE = (Argument) Argument.of(Publisher.class);

    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final ConversionService conversionService;

    /**
     * @param conversionService            The conversion service
     * @param httpContentProcessorResolver The http content processor resolver
     */
    public PublisherBodyBinder(ConversionService conversionService,
                               HttpContentProcessorResolver httpContentProcessorResolver) {
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.conversionService = conversionService;
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Publisher<?>> bind(ArgumentConversionContext<Publisher<?>> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nhr) {
            if (nhr.rootBody() instanceof ImmediateByteBody imm && imm.empty()) {
                return BindingResult.empty();
            }
            Argument<?> targetType = context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            try {
                Publisher<?> publisher = nhr.rootBody()
                    .processMulti(httpContentProcessorResolver.resolve(nhr, targetType).resultType(context.getArgument()))
                    .mapNotNull(o -> {
                        ArgumentConversionContext<?> conversionContext = context.with(targetType);
                        return convertAndRelease(conversionService, conversionContext, o);
                    })
                    .asPublisher();
                return () -> Optional.of(publisher);
            } catch (Throwable t) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Server received error for argument [" + context.getArgument() + "]: " + t.getMessage(), t);
                }
                return () -> Optional.of(Mono.error(t));
            }
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

    /**
     * This method converts a potentially
     * {@link io.netty.util.ReferenceCounted netty reference counted} and transfers release
     * ownership to the new object.
     *
     * @param conversionService The conversion service
     * @param conversionContext The context to convert to
     * @param o The object to convert
     * @return The converted object
     */
    static Object convertAndRelease(ConversionService conversionService, ArgumentConversionContext<?> conversionContext, Object o) {
        if (o instanceof ByteBufHolder holder) {
            o = holder.content();
            if (!((ByteBuf) o).isReadable()) {
                return null;
            }
        }

        Optional<?> converted;
        if (o instanceof io.netty.util.ReferenceCounted rc) {
            converted = NettyConverters.refCountAwareConvert(conversionService, rc, conversionContext);
        } else {
            converted = conversionService.convert(o, conversionContext);
        }
        if (converted.isPresent()) {
            return converted.get();
        } else {
            throw extractError(o, conversionContext);
        }
    }
}
