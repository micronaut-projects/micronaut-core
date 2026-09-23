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

import io.micronaut.context.BeanProvider;
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
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.binding.ServerRequestBody;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.converters.NettyConverters;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
final class NettyBodyAnnotationBinder<T> extends DefaultBodyAnnotationBinder<T> {
    final NettyHttpServerConfiguration httpServerConfiguration;
    final MessageBodyHandlerRegistry bodyHandlerRegistry;
    final BeanProvider<FormFactory> formFactory;

    NettyBodyAnnotationBinder(ConversionService conversionService,
                              NettyHttpServerConfiguration httpServerConfiguration,
                              MessageBodyHandlerRegistry bodyHandlerRegistry,
                              BeanProvider<FormFactory> formFactory) {
        super(conversionService);
        this.httpServerConfiguration = httpServerConfiguration;
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.formFactory = formFactory;
    }

    @Override
    protected BindingResult<T> bindBodyPart(ArgumentConversionContext<T> context, HttpRequest<?> source, String bodyComponent) {
        if (source instanceof FormCapableHttpRequest<?> nhr && nhr.hasFormBody()) {
            // skipClaimed=true because for unmatched binding, both this binder and PartUploadAnnotationBinder can be called on the same parameter
            return NettyPartUploadAnnotationBinder.bindPart(conversionService, context, formFactory.get(), nhr, bodyComponent, true);
        } else {
            return super.bindBodyPart(context, source, bodyComponent);
        }
    }

    /**
     * The server request whose bytes are the body of the request a route is bound with: the Netty
     * request itself, the server request that a request a filter continued with is or wraps,
     * see {@link ServerRequestBody}, or the Netty request of its mutable view, unless the filter
     * set the body to an object, which the default binder converts, like before.
     *
     * @param source The request
     * @return The server request, or {@code null} if the body is not read from bytes
     */
    static @Nullable ServerHttpRequest<?> bodyOf(HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nhr) {
            return nhr;
        }
        if (source.getBody().isPresent()) {
            return null;
        }
        ServerHttpRequest<?> server = ServerRequestBody.of(source);
        if (server == null) {
            // e.g. the mutable view of the Netty request, which a filter continued with
            return NettyHttpRequest.findBodyRequest(source);
        }
        return server;
    }

    @Override
    protected BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> nhr)) {
            if (bodyOf(source) == null) {
                return super.bindFullBodyConvertibleValues(source);
            }
            // a request a filter continued with: read from its bytes, not cached
            //noinspection unchecked
            return (BindingResult<ConvertibleValues<?>>) bindFullBody((ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), source);
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
        ServerHttpRequest<?> server = bodyOf(source);
        if (server == null) {
            return super.bindFullBody(context, source);
        }
        if (server.byteBody().expectedLength().orElse(-1) == 0) {
            return bindDefaultValue(context);
        }

        // If there's an error during conversion, the body must stay available, so we split here.
        // This costs us nothing because we need to buffer anyway.
        ByteBody body = server.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
        ExecutionFlow<? extends CloseableAvailableByteBody> buffered = InternalByteBody.bufferFlow(body);

        return new PendingRequestBindingResult<>() {
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            @Nullable
            Optional<T> result;

            {
                // NettyRequestLifecycle will "subscribe" to the execution flow added to routeWaitsFor,
                // so we can't subscribe directly ourselves. Instead, use the side effect of a map.
                BasicHttpAttributes.addRouteWaitsFor(source, buffered.flatMap(imm ->
                    PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(source)).propagate(() -> {
                        try {
                            result = transform(source, server, context, imm);
                            return ExecutionFlow.just(null);
                        } catch (Throwable e) {
                            return ExecutionFlow.error(e);
                        }
                    })));
            }

            @SuppressWarnings("OptionalAssignedToNull")
            @Override
            public boolean isPending() {
                return result == null;
            }

            @Override
            public Optional<T> getValue() {
                return result == null ? Optional.empty() : result;
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }
        };
    }

    /**
     * Read the body.
     *
     * @param request The request the route is bound with
     * @param server  The server request whose bytes are the body, see {@link #bodyOf(HttpRequest)}
     * @param context The conversion context
     * @param imm     The bytes
     * @return The body
     * @throws Throwable If the body cannot be read
     */
    Optional<T> transform(HttpRequest<?> request, ServerHttpRequest<?> server, ArgumentConversionContext<T> context, AvailableByteBody imm) throws Throwable {
        // the form is decoded by the Netty request whose bytes are the body, e.g. of the mutable
        // view a filter continued with after it changed the URI in place
        NettyHttpRequest<?> formRequest = server instanceof NettyHttpRequest<?> netty ? netty : NettyHttpRequest.findBodyRequest(server);
        // the decoded body is kept by the Netty request it is read from, not by a request a filter
        // continued with
        NettyHttpRequest<?> nhr = request == server && formRequest == server ? formRequest : null;
        MessageBodyReader<T> reader = null;
        final RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(request).orElse(null);
        if (routeInfo != null) {
            reader = (MessageBodyReader<T>) routeInfo.getMessageBodyReader();
        }
        MediaType mediaType = request.getContentType().orElse(null);
        if (mediaType != null && (reader == null || !reader.isReadable(context.getArgument(), mediaType))) {
            reader = bodyHandlerRegistry.findReader(context.getArgument(), List.of(mediaType)).orElse(null);
        }
        if (reader == null && formRequest != null && formRequest.hasFormBody()) {
            Map<String, List<CloseableByteBody>> bodies = new LinkedHashMap<>();
            for (RawFormField rff : toListNow(formRequest.getRawFormFields(imm))) {
                bodies.computeIfAbsent(rff.metadata().name(), k -> new ArrayList<>(1)).add(rff.byteBody());
            }
            Object intermediate = io.micronaut.http.server.multipart.FormRouteCompleter.mapForGetBody(bodies, formRequest.getCharacterEncoding());
            Optional<T> converted = conversionService.convert(intermediate, context);
            if (nhr != null) {
                nhr.setLegacyBody(converted.orElse(null));
            }
            return converted;
        }
        if (reader != null) {
            T result = read(context, reader, request.getHeaders(), mediaType, imm.toByteBuffer());
            if (nhr != null) {
                nhr.setLegacyBody(result);
            }
            return Optional.ofNullable(result);
        }
        ByteBuf byteBuf = NettyByteBodyFactory.toByteBuf(imm);
        Optional<T> converted = conversionService.convert(byteBuf, ByteBuf.class, context.getArgument().getType(), context);
        NettyConverters.postProcess(byteBuf, converted);
        if (nhr != null) {
            nhr.setLegacyBody(converted.orElse(null));
        }
        return converted;
    }

    private static <T> List<T> toListNow(Flux<T> flux) {
        var sub = new Subscriber<T>() {
            final List<T> list = new ArrayList<>();
            boolean complete = false;
            @Nullable
            Throwable error = null;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                list.add(t);
            }

            @Override
            public void onError(Throwable t) {
                error = t;
                complete = true;
            }

            @Override
            public void onComplete() {
                complete = true;
            }
        };
        flux.subscribe(sub);
        if (!sub.complete) {
            throw new IllegalStateException("Flux did not finish immediately");
        }
        if (sub.error != null) {
            throw new IllegalStateException("Failed to load form fields", sub.error);
        }
        return sub.list;
    }

    @Nullable
    private T read(ArgumentConversionContext<T> context, MessageBodyReader<T> reader, HttpHeaders headers, @Nullable MediaType mediaType, ByteBuffer<?> byteBuffer) {
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
