/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.AsyncServerHttpRequest;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.server.exceptions.UnsupportedMediaException;
import io.micronaut.web.router.builder.AsyncHandlerRequest;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * The {@link AsyncServerHttpRequest} of a route invocation: a view of the server request that
 * owns the one read of its body. Everything but the body is the request's, so the view works
 * over any server request, and filters see the same request.
 *
 * <p>The body is read from the {@link ServerHttpRequest#byteBody()} of the request: decoded
 * through the {@code @Body} binders, outside the argument binding of the route, or moved to the
 * reader that was asked for.</p>
 *
 * @param <B> The body type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultAsyncServerHttpRequest<B> extends HttpRequestWrapper<B> implements AsyncServerHttpRequest<B>, AsyncHandlerRequest {

    private static final List<String> ELEMENT_MEDIA_TYPES = List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM);
    private static final List<String> FORM_MEDIA_TYPES = List.of(MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA);

    private final ServerHttpRequest<B> request;
    private final AsyncServerHttpRequestArgumentBinder binder;

    // guarded by this
    private @Nullable String reader;
    private @Nullable Supplier<CompletionStage<Void>> release;

    DefaultAsyncServerHttpRequest(ServerHttpRequest<B> request, AsyncServerHttpRequestArgumentBinder binder) {
        super(request);
        this.request = request;
        this.binder = binder;
    }

    @Override
    public ByteBody byteBody() {
        return request.byteBody();
    }

    @Override
    public ByteBodyFactory byteBodyFactory() {
        return request.byteBodyFactory();
    }

    @Override
    public MutableHttpRequest<B> mutate() {
        return request.mutate();
    }

    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        return request.getAttribute(name);
    }

    @Override
    public Charset getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    @Override
    public Optional<MediaType> getContentType() {
        return request.getContentType();
    }

    @Override
    public long getContentLength() {
        return request.getContentLength();
    }

    @Override
    public Optional<B> getBody() {
        // no binder decodes a body for an asynchronous handler: it reads the body itself
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getBody(ArgumentConversionContext<T> conversionContext) {
        return Optional.empty();
    }

    @Override
    public boolean hasBody() {
        OptionalLong length = request.byteBody().expectedLength();
        return length.isEmpty() || length.getAsLong() != 0;
    }

    @Override
    public OptionalLong expectedBodySize() {
        return request.byteBody().expectedLength();
    }

    @Override
    public <T> CompletionStage<T> body(Class<T> type) {
        return body(Argument.of(type));
    }

    @Override
    public <T> CompletionStage<T> body(Argument<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isAsyncOrReactive()) {
            throw new IllegalArgumentException("The body cannot be read as the reactive or asynchronous type " + type.getTypeName()
                + ": read its elements one at a time with elements(), or take it with takeBody()");
        }
        if (InputStream.class.isAssignableFrom(type.getType())) {
            throw new IllegalArgumentException("The body cannot be read as an InputStream by an asynchronous handler, as reading it blocks"
                + ": take it with takeBody(), or write it to a file with transferTo()");
        }
        claim("body");
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        // bound like the @Body argument of a controller, with the binder of the type
        Argument<T> argument = HandlerMethod.bodyArgument(type);
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            ArgumentBinder<T, HttpRequest<?>> argumentBinder = binder.binderRegistry().findArgumentBinder(argument)
                .orElseThrow(() -> UnsatisfiedRouteException.create(argument));
            ArgumentConversionContext<T> context = ConversionContext.of(argument, request.getLocale().orElse(null), request.getCharacterEncoding());
            @SuppressWarnings("unchecked")
            ArgumentBinder.BindingResult<T>[] bound = new ArgumentBinder.BindingResult[1];
            // the binder waits for the body: this waits for it here, not the route, which is running
            ExecutionFlow<?> waitsFor = BasicHttpAttributes.detachRouteWaitsFor(request, () -> bound[0] = argumentBinder.bind(context, request));
            waitsFor.onComplete((ignored, error) -> {
                if (error != null) {
                    complete(propagatedContext, result, null, error);
                    return;
                }
                T value;
                try {
                    value = value(argument, context, Objects.requireNonNull(bound[0], "binding result"));
                } catch (Throwable e) {
                    complete(propagatedContext, result, null, e);
                    return;
                }
                complete(propagatedContext, result, value, null);
            });
        } catch (Throwable e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    @Override
    public CompletionStage<String> text() {
        claim("text");
        UploadContext context = uploadContext();
        return inRouteContext(content(context).text(context.maxBufferSize()));
    }

    @Override
    public CompletionStage<String> text(int maximumBytes) {
        checkLimit(maximumBytes);
        claim("text");
        return inRouteContext(content(uploadContext()).text(maximumBytes));
    }

    @Override
    public CompletionStage<byte[]> bytes(int maximumBytes) {
        checkLimit(maximumBytes);
        claim("bytes");
        return inRouteContext(content(uploadContext()).bytes(maximumBytes));
    }

    @Override
    public CompletionStage<Void> transferTo(Path destination) {
        Objects.requireNonNull(destination, "destination");
        claim("transferTo");
        return inRouteContext(content(uploadContext()).transferTo(destination));
    }

    @Override
    public <T> BodyElements<T> elements(Class<T> type) {
        return elements(Argument.of(type));
    }

    @Override
    public <T> BodyElements<T> elements(Argument<T> type) {
        Objects.requireNonNull(type, "type");
        claim("elements");
        CloseableByteBody body = request.byteBody().move();
        PublisherBodyElements<T> elements = new PublisherBodyElements<>(() -> elementPublisher(type, body), body::close);
        owned(elements::closeAsync, elements::close);
        return elements;
    }

    @Override
    public CompletionStage<FormData> form() {
        claim("form");
        try {
            return inRouteContext(FormDataArgumentBinder.collect(binder.formFactory(), binder.conversionService, formRequest()));
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public FormParts parts() {
        claim("parts");
        FormCapableHttpRequest<?> formRequest = formRequest();
        DefaultFormParts parts = new DefaultFormParts(formRequest, UploadContext.of(binder.formFactory(), formRequest));
        owned(parts::closeAsync, parts::close);
        return parts;
    }

    @Override
    public CloseableByteBody takeBody() {
        claim("takeBody");
        return request.byteBody().move();
    }

    @Override
    public CompletionStage<Void> discardBody() {
        claim("discardBody");
        request.byteBody().move().close();
        return CompletableFuture.completedStage(null);
    }

    /**
     * Release what the reader of the body left open when the handler completed: the parts of a
     * form, the elements of the body, or an operation on the body that is still running.
     *
     * @return Completes when released
     */
    @Override
    public CompletionStage<Void> releaseBody() {
        Supplier<CompletionStage<Void>> r;
        synchronized (this) {
            r = release;
        }
        return r == null ? CompletableFuture.completedStage(null) : r.get();
    }

    @Override
    public String toString() {
        return request.toString();
    }

    /**
     * The stage of a read of the whole body, which completes with the propagated context of the
     * route in scope: the handler reads the body where a controller method receives it, so what
     * the handler continues with runs with the context the controller method runs with, e.g. the
     * MDC context a filter added, even when the body arrives later on another thread.
     *
     * @param stage The stage of the read
     * @param <T>   The type of the result
     * @return The stage
     */
    private static <T> CompletionStage<T> inRouteContext(CompletionStage<T> stage) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        if (propagatedContext.isEmpty()) {
            return stage;
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        stage.whenComplete((value, error) -> complete(propagatedContext, result, value, error));
        return result;
    }

    /**
     * Complete the stage of a read with the propagated context in scope, so that the
     * continuations of the handler run with it.
     *
     * @param propagatedContext The propagated context of the route
     * @param result            The stage
     * @param value             The value
     * @param error             The error, or {@code null}
     * @param <T>               The type of the value
     */
    private static <T> void complete(PropagatedContext propagatedContext, CompletableFuture<T> result, @Nullable T value, @Nullable Throwable error) {
        Runnable completion = error != null ? () -> result.completeExceptionally(error) : () -> result.complete(value);
        if (propagatedContext.isEmpty() || propagatedContext.isBound()) {
            completion.run();
        } else {
            propagatedContext.propagate(completion);
        }
    }

    /**
     * Claim the body for a reader: the first one owns it.
     *
     * @param name The method that reads the body
     */
    private synchronized void claim(String name) {
        String first = reader;
        if (first != null) {
            throw new IllegalStateException("The body of the request was already read with " + first + "(): it can be read once");
        }
        reader = name;
    }

    /**
     * Keep what the reader of the body must release when the handler completes, and when the
     * request ends.
     */
    private void owned(Supplier<CompletionStage<Void>> closeAsync, Runnable close) {
        synchronized (this) {
            release = closeAsync;
        }
        if (request instanceof LifecycleHttpRequest<?> lifecycle) {
            lifecycle.addDisposalResource(close);
        }
    }

    private UploadContext uploadContext() {
        return UploadContext.of(binder.formFactory(), request);
    }

    /**
     * The body, moved to content read like a form field: in memory with a limit, or to a file.
     */
    private UploadContent content(UploadContext context) {
        StreamingUploadContent content = StreamingUploadContent.requestBody(request.byteBody().move(), request.getContentType().orElse(null), context);
        owned(content::closeAsync, content::closeAsync);
        return content;
    }

    private FormCapableHttpRequest<?> formRequest() {
        if (request instanceof FormCapableHttpRequest<?> formRequest && formRequest.hasFormBody()) {
            return formRequest;
        }
        throw new UnsupportedMediaException(String.valueOf(request.getContentType().orElse(null)), FORM_MEDIA_TYPES);
    }

    private <T> Publisher<? extends T> elementPublisher(Argument<T> type, CloseableByteBody body) {
        MediaType contentType = request.getContentType().orElse(null);
        MessageBodyReader<T> reader = isJson(contentType) ? binder.bodyHandlerRegistry().findReader(type, List.of(contentType)).orElse(null) : null;
        if (!(reader instanceof ChunkedMessageBodyReader<T> chunked)) {
            throw new UnsupportedMediaException(String.valueOf(contentType), ELEMENT_MEDIA_TYPES);
        }
        return chunked.readChunked(type, contentType, request.getHeaders(), body.toByteBufferPublisher());
    }

    /**
     * @return Whether the elements of a body of the media type are JSON values: a JSON array or a
     * JSON stream
     */
    private static boolean isJson(@Nullable MediaType contentType) {
        return contentType != null && (contentType.matches(MediaType.APPLICATION_JSON_TYPE)
            || contentType.matches(MediaType.APPLICATION_JSON_STREAM_TYPE)
            || contentType.getSubtype().endsWith("+json"));
    }

    private static void checkLimit(int maximumBytes) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("The maximum number of bytes must not be negative: " + maximumBytes);
        }
    }

    /**
     * The value of a body binding that completed, like the argument binding of a route takes it.
     */
    @SuppressWarnings("unchecked")
    private <T> @Nullable T value(Argument<T> argument, ArgumentConversionContext<T> context, ArgumentBinder.BindingResult<T> result) {
        List<ConversionError> errors = result.getConversionErrors();
        if (!errors.isEmpty()) {
            throw new ConversionErrorException(argument, errors.get(0));
        }
        Optional<ConversionError> lastError = context.getLastError();
        if (lastError.isPresent()) {
            throw new ConversionErrorException(argument, lastError.get());
        }
        if (!(result instanceof PendingRequestBindingResult<T> pending && pending.isPending()) && result.isPresentAndSatisfied()) {
            Object value = result.get();
            if (value instanceof ConversionError error) {
                throw new ConversionErrorException(argument, error);
            }
            if (argument.getType().isInstance(value)) {
                return (T) value;
            }
            ArgumentConversionContext<T> conversion = ConversionContext.of(argument);
            Optional<T> converted = binder.conversionService.convert(value, argument.getType(), conversion);
            if (converted.isPresent()) {
                return converted.get();
            }
            throw conversion.getLastError()
                .map(error -> (RuntimeException) new ConversionErrorException(argument, error))
                .orElseGet(() -> UnsatisfiedRouteException.create(argument));
        }
        if (argument.isOptional()) {
            return (T) Optional.empty();
        }
        if (argument.isNullable()) {
            return null;
        }
        throw UnsatisfiedRouteException.create(argument);
    }
}
