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
package io.micronaut.http.server.binding;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Singleton;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.DelayedExecutionFlow;
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
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.stream.ReactorInterop;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormFieldFlows;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server body binding independent of the transport, with hooks for native conversion and legacy state.
 *
 * @param <T> The bound type
 */
@Internal
@Singleton
public class ServerBodyAnnotationBinder<T> extends DefaultBodyAnnotationBinder<T> {
    final MessageBodyHandlerRegistry bodyHandlerRegistry;
    private final BeanProvider<FormFactory> formFactory;

    /**
     * @param conversionService Conversion service
     * @param bodyHandlerRegistry Body readers
     * @param formFactory Form utilities
     */
    public ServerBodyAnnotationBinder(ConversionService conversionService,
                              MessageBodyHandlerRegistry bodyHandlerRegistry,
                              BeanProvider<FormFactory> formFactory) {
        super(conversionService);
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.formFactory = formFactory;
    }

    @Override
    protected BindingResult<T> bindBodyPart(ArgumentConversionContext<T> context, HttpRequest<?> source, String bodyComponent) {
        // the request itself, or e.g. the mutable view of the request that a filter continued with
        FormCapableHttpRequest<?> nhr = FormBinding.formRequest(source);
        if (nhr != null && nhr.hasFormBody()) {
            // skipClaimed=true because for unmatched binding, both this binder and PartUploadAnnotationBinder can be called on the same parameter
            return PartUploadAnnotationBinder.bindPart(conversionService, context, formFactory.get(), source, nhr, bodyComponent, true);
        } else {
            return super.bindBodyPart(context, source, bodyComponent);
        }
    }

    /**
     * The server request whose bytes are the body of the request a route is bound with:
     * the server request that a request a filter continued with is or wraps,
     * see {@link ServerRequestBody}, e.g. the mutable copy of the Netty request, unless the filter
     * set the body to an object, which the default binder converts, like before, or to
     * {@code null}, which is no body.
     *
     * @param source The request
     * @return The server request, or {@code null} if the body is not read from bytes
     */
    public @Nullable ServerHttpRequest<?> bodyOf(HttpRequest<?> source) {
        if (source.getBody().isPresent()) {
            // the body a filter set
            return null;
        }
        // none when a filter cleared the body
        return ServerRequestBody.of(source);
    }

    @Override
    protected BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        if (bodyOf(source) == null) {
            return super.bindFullBodyConvertibleValues(source);
        }
        //noinspection unchecked
        return (BindingResult<ConvertibleValues<?>>) bindFullBody((ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), source);
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

        var pending = new PendingRequestBindingResult<T>() {
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            @Nullable
            Optional<T> result;
            boolean convertedToArgumentType;

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

            @Override
            public boolean isConvertedToArgumentType() {
                return convertedToArgumentType;
            }
        };
        // The request lifecycle will "subscribe" to the execution flow added to routeWaitsFor,
        // so we can't subscribe directly ourselves. Instead, use the side effect of a map.
        BasicHttpAttributes.addRouteWaitsFor(source, buffered.flatMap(imm ->
            ServerHttpRequestContext.withRequest(PropagatedContext.getOrEmpty(), source).propagate(() -> {
                try {
                    MessageBodyReader<T> reader = findReader(source, context);
                    // A body reader produces the value for the complete argument, including type arguments
                    pending.convertedToArgumentType = reader != null;
                    return transform(source, server, context, reader, imm).map(value -> {
                        pending.result = value;
                        return null;
                    });
                } catch (Throwable e) {
                    return ExecutionFlow.error(e);
                }
            })));
        return pending;
    }

    /**
     * Read the body.
     *
     * @param request The request the route is bound with
     * @param server  The server request whose bytes are the body, see {@link #bodyOf(HttpRequest)}
     * @param context The conversion context
     * @param imm     The bytes
     * @return The body
     */
    ExecutionFlow<Optional<T>> transform(HttpRequest<?> request, ServerHttpRequest<?> server, ArgumentConversionContext<T> context, AvailableByteBody imm) {
        return transform(request, server, context, findReader(request, context), imm);
    }

    private @Nullable MessageBodyReader<T> findReader(HttpRequest<?> request, ArgumentConversionContext<T> context) {
        MessageBodyReader<T> reader = null;
        final RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(request).orElse(null);
        if (routeInfo != null) {
            reader = (MessageBodyReader<T>) routeInfo.getMessageBodyReader();
        }
        MediaType mediaType = request.getContentType().orElse(null);
        if (mediaType != null && (reader == null || !reader.isReadable(context.getArgument(), mediaType))) {
            reader = bodyHandlerRegistry.findReader(context.getArgument(), List.of(mediaType)).orElse(null);
        }
        return reader;
    }

    private ExecutionFlow<Optional<T>> transform(HttpRequest<?> request, ServerHttpRequest<?> server, ArgumentConversionContext<T> context, @Nullable MessageBodyReader<T> reader, AvailableByteBody imm) {
        FormCapableHttpRequest<?> formRequest = formRequest(server);
        MediaType mediaType = request.getContentType().orElse(null);
        if (reader == null && formRequest != null && formRequest.hasFormBody()) {
            Map<@Nullable String, Object> values = new LinkedHashMap<>();
            // Read one field at a time without waiting on the decoding or buffering thread.
            // The map is only touched by the serialized completion chain.
            DelayedExecutionFlow<Void> collected = DelayedExecutionFlow.create();
            var collector = new FormFieldFlows.Concat<RawFormField, Void>(field ->
                InternalByteBody.bufferFlow(field.byteBody()).map(bytes -> {
                    try (bytes) {
                        addFormValue(values, field.metadata().name(), bytes.toString(formRequest.getCharacterEncoding()));
                    }
                    return null;
                }), RawFormField::close,
                ignored -> { },
                error -> {
                    if (error == null) {
                        collected.complete(null);
                    } else {
                        collected.completeExceptionally(new IllegalStateException("Failed to load form fields", error));
                    }
                });
            collected.onCancel(collector::cancel);
            // Buffered fields can still come from a foreign Reactor publisher. Supply the
            // discard hook so fields queued by that publisher are released on cancellation.
            ReactorInterop.subscribe(formRequest.getRawFormFields(imm), collector, null,
                item -> {
                    if (item instanceof RawFormField field) {
                        field.close();
                    }
                });
            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
            return collected.map(ignored ->
                ServerHttpRequestContext.withRequest(propagatedContext, request).propagate(() -> {
                    Optional<T> converted = conversionService.convert(values, context);
                    cacheDecodedBody(request, server, converted.orElse(null));
                    return converted;
                }));
        }
        if (reader != null) {
            T result = read(context, reader, request.getHeaders(), mediaType, imm.toByteBuffer());
            cacheDecodedBody(request, server, result);
            return ExecutionFlow.just(Optional.ofNullable(result));
        }
        Optional<T> converted = convertNative(context, imm);
        cacheDecodedBody(request, server, converted.orElse(null));
        return ExecutionFlow.just(converted);
    }

    @SuppressWarnings("unchecked")
    private static void addFormValue(Map<@Nullable String, Object> values, @Nullable String name, String text) {
        Object previous = values.putIfAbsent(name, text);
        if (previous instanceof List<?> repeated) {
            ((List<String>) repeated).add(text);
        } else if (previous instanceof String first) {
            List<String> repeated = new ArrayList<>(2);
            repeated.add(first);
            repeated.add(text);
            values.put(name, repeated);
        }
    }

    /**
     * Resolve the decoder of the body being bound. Transports may preserve their original decoder.
     *
     * @param server The request supplying bytes
     * @return The form-capable request, or {@code null}
     */
    protected @Nullable FormCapableHttpRequest<?> formRequest(ServerHttpRequest<?> server) {
        return FormBinding.formRequest(server);
    }

    /**
     * Store a legacy decoded body, if the transport exposes one. The default stores nothing.
     *
     * @param request The request being bound
     * @param server The request supplying bytes
     * @param value The decoded body
     */
    protected void cacheDecodedBody(HttpRequest<?> request, ServerHttpRequest<?> server, @Nullable Object value) {
    }

    /**
     * Convert bytes when no message body reader is available. Transports may override this to
     * preserve native-buffer converters and their ownership contract.
     *
     * @param context Conversion context
     * @param body The buffered bytes
     * @return The converted value
     */
    protected Optional<T> convertNative(ArgumentConversionContext<T> context, AvailableByteBody body) {
        return conversionService.convert(body.toByteArray(), context);
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
