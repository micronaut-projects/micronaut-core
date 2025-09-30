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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.netty.FormDataHttpContentProcessor;
import io.micronaut.http.server.netty.FormRouteCompleter;
import io.micronaut.http.server.netty.MicronautHttpData;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.converters.NettyConverters;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
final class NettyBodyAnnotationBinder<T> extends DefaultBodyAnnotationBinder<T> {
    final NettyHttpServerConfiguration httpServerConfiguration;
    final MessageBodyHandlerRegistry bodyHandlerRegistry;

    NettyBodyAnnotationBinder(ConversionService conversionService,
                              NettyHttpServerConfiguration httpServerConfiguration,
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
        BindingResult<ConvertibleValues<?>> existing = nhr.convertibleBody;
        if (existing != null) {
            return existing;
        } else {
            //noinspection unchecked
            BindingResult<ConvertibleValues<?>> result = (BindingResult<ConvertibleValues<?>>) bindFullBody((ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), nhr);
            nhr.convertibleBody = result;
            return result;
        }
    }

    @Override
    public BindingResult<T> bindFullBody(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> nhr)) {
            return super.bindFullBody(context, source);
        }
        if (nhr.byteBody().expectedLength().orElse(-1) == 0) {
            return bindDefaultValue(context);
        }

        // If there's an error during conversion, the body must stay available, so we split here.
        // This costs us nothing because we need to buffer anyway.
        ByteBody body = nhr.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
        ExecutionFlow<? extends CloseableAvailableByteBody> buffered = InternalByteBody.bufferFlow(body);

        return new PendingRequestBindingResult<>() {
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            Optional<T> result;

            {
                // NettyRequestLifecycle will "subscribe" to the execution flow added to routeWaitsFor,
                // so we can't subscribe directly ourselves. Instead, use the side effect of a map.
                nhr.addRouteWaitsFor(buffered.flatMap(imm -> {
                    try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(nhr)).propagate()) {
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

    Optional<T> transform(NettyHttpRequest<?> nhr, ArgumentConversionContext<T> context, AvailableByteBody imm) throws Throwable {
        MessageBodyReader<T> reader = null;
        final RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(nhr).orElse(null);
        if (routeInfo != null) {
            reader = (MessageBodyReader<T>) routeInfo.getMessageBodyReader();
        }
        MediaType mediaType = nhr.getContentType().orElse(null);
        if (mediaType != null && (reader == null || !reader.isReadable(context.getArgument(), mediaType))) {
            reader = bodyHandlerRegistry.findReader(context.getArgument(), List.of(mediaType)).orElse(null);
        }
        if (reader == null && nhr.isFormOrMultipartData()) {
            FormDataHttpContentProcessor processor = new FormDataHttpContentProcessor(nhr, httpServerConfiguration);
            ByteBuf buf = NettyByteBodyFactory.toByteBuf(imm);
            List<InterfaceHttpData> data = new ArrayList<>();
            if (buf.isReadable()) {
                processor.add(new DefaultLastHttpContent(buf), data);
            } else {
                buf.release();
            }
            processor.complete(data);
            boolean allFormData = true;
            for (Object object : data) {
                if (!(object instanceof MicronautHttpData<?>)) {
                    allFormData = false;
                    break;
                }
            }
            Object intermediate;
            if (allFormData) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                List<? extends MicronautHttpData<?>> formData = (List) data;
                Map<String, Object> map = FormRouteCompleter.toMap(httpServerConfiguration.getDefaultCharset(), formData);
                for (MicronautHttpData<?> datum : formData) {
                    datum.release();
                }
                intermediate = map;
            } else if (data.size() == 1) {
                intermediate = data.get(0);
                if (intermediate instanceof ByteBufHolder bbh) {
                    intermediate = bbh.content();
                }
            } else {
                intermediate = coerceToComposite(data, nhr.getChannelHandlerContext().alloc());
            }
            Optional<T> converted =
                intermediate instanceof io.netty.util.ReferenceCounted rc ?
                    NettyConverters.refCountAwareConvert(conversionService, rc, context) :
                    conversionService.convert(intermediate, context);
            nhr.setLegacyBody(converted.orElse(null));
            return converted;
        }
        if (reader != null) {
            T result = read(context, reader, nhr.getHeaders(), mediaType, imm.toByteBuffer());
            nhr.setLegacyBody(result);
            return Optional.ofNullable(result);
        }
        ByteBuf byteBuf = NettyByteBodyFactory.toByteBuf(imm);
        Optional<T> converted = conversionService.convert(byteBuf, ByteBuf.class, context.getArgument().getType(), context);
        NettyConverters.postProcess(byteBuf, converted);
        nhr.setLegacyBody(converted.orElse(null));
        return converted;
    }

    private static CompositeByteBuf coerceToComposite(List<?> objects, ByteBufAllocator alloc) {
        CompositeByteBuf composite = alloc.compositeBuffer();
        for (Object object : objects) {
            composite.addComponent(true, (ByteBuf) object);
        }
        return composite;
    }

    private T read(ArgumentConversionContext<T> context, MessageBodyReader<T> reader, HttpHeaders headers, MediaType mediaType, ByteBuffer<?> byteBuffer) {
        boolean success = false;
        try {
            T result = reader.read(context.getArgument(), mediaType, headers, byteBuffer);
            success = true;
            return result;
        } catch (CodecException ce) {
            if (ce.getCause() instanceof Exception e) {
                context.reject(e);
            } else {
                context.reject(ce);
            }
            return null;
        } finally {
            if (!success && byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
        }
    }
}
