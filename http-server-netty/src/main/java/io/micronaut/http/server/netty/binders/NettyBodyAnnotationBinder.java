/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.DefaultHttpContentProcessorResolver;
import io.micronaut.http.server.netty.FormDataHttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.body.ImmediateByteBody;

import java.util.List;
import java.util.Optional;

final class NettyBodyAnnotationBinder<T> extends DefaultBodyAnnotationBinder<T> {
    private static final CharSequence ATTR_CONVERTIBLE_BODY = "NettyBodyAnnotationBinder.convertibleBody";

    final HttpServerConfiguration httpServerConfiguration;
    final MessageBodyHandlerRegistry bodyHandlerRegistry;

    NettyBodyAnnotationBinder(ConversionService conversionService,
                                     HttpServerConfiguration httpServerConfiguration,
                                     MessageBodyHandlerRegistry bodyHandlerRegistry) {
        super(conversionService);
        this.httpServerConfiguration = httpServerConfiguration;
        this.bodyHandlerRegistry = bodyHandlerRegistry;
    }

    @Override
    protected BindingResult<T> bindBodyPart(ArgumentConversionContext<T> context, HttpRequest<?> source, String bodyComponent) {
        if (source instanceof NettyHttpRequest<?> nhr && nhr.isFormOrMultipartData()) {
            // skipClaimed=true because for unmatched binding, both this binder and PartUploadAnnotationBinder can be called on the same parameter
            return NettyPartUploadAnnotationBinder.bindPart(conversionService, context, nhr, bodyComponent, true);
        } else {
            return super.bindBodyPart(context, source, bodyComponent);
        }
    }

    @Override
    protected BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> nhr)) {
            return super.bindFullBodyConvertibleValues(source);
        }
        Optional<Object> existing = nhr.getAttribute(ATTR_CONVERTIBLE_BODY);
        if (existing.isPresent()) {
            return (BindingResult<ConvertibleValues<?>>) existing.get();
        } else {
            //noinspection unchecked
            BindingResult<ConvertibleValues<?>> result = (BindingResult<ConvertibleValues<?>>) bindFullBody((ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), nhr);
            nhr.setAttribute(ATTR_CONVERTIBLE_BODY, result);
            return result;
        }
    }

    @Override
    public BindingResult<T> bindFullBody(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> nhr)) {
            return super.bindFullBody(context, source);
        }
        if (nhr.byteBody() instanceof ImmediateByteBody imm && imm.empty()) {
            return BindingResult.empty();
        }

        ExecutionFlow<ImmediateByteBody> buffered = nhr.byteBody()
            .buffer(nhr.getChannelHandlerContext().alloc());

        return new PendingRequestBindingResult<T>() {
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            Optional<T> result;

            {
                // NettyRequestLifecycle will "subscribe" to the execution flow added to routeWaitsFor,
                // so we can't subscribe directly ourselves. Instead, use the side effect of a map.
                nhr.addRouteWaitsFor(buffered.flatMap(imm -> {
                    try {
                        result = transform(nhr, context, imm);
                        return ExecutionFlow.just(null);
                    } catch (Throwable e) {
                        return ExecutionFlow.error(e);
                    }
                }));
            }

            @SuppressWarnings("OptionalAssignedToNull")
            @Override
            public boolean isPending() {
                return result == null;
            }

            @Override
            public Optional<T> getValue() {
                return result;
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }
        };
    }

    Optional<T> transform(NettyHttpRequest<?> nhr, ArgumentConversionContext<T> context, ImmediateByteBody imm) throws Throwable {
        if (!DefaultHttpContentProcessorResolver.isRaw(context.getArgument())) {
            if (nhr.isFormOrMultipartData()) {
                return imm.processSingle(
                        new FormDataHttpContentProcessor(nhr, httpServerConfiguration),
                        httpServerConfiguration.getDefaultCharset(),
                        nhr.getChannelHandlerContext().alloc()
                    )
                    .convert(conversionService, context)
                    .map(o -> (T) o.claimForExternal());
            }

            MediaType mediaType = nhr.getContentType().orElse(null);
            if (mediaType != null) {
                Optional<MessageBodyReader<T>> reader = bodyHandlerRegistry.findReader(context.getArgument(), List.of(mediaType));
                if (reader.isPresent()) {
                    try {
                        //noinspection unchecked
                        return Optional.ofNullable((T) imm.processSingle(httpServerConfiguration, reader.get(), context.getArgument(), mediaType, nhr.getHeaders()).claimForExternal());
                    } catch (CodecException ce) {
                        if (ce.getCause() instanceof Exception e) {
                            context.reject(e);
                        } else {
                            context.reject(ce);
                        }
                        return Optional.empty();
                    }
                }
            }
        }
        //noinspection unchecked
        return imm.rawContent(httpServerConfiguration)
            .convert(conversionService, context)
            .map(o -> (T) o.claimForExternal());
    }
}
