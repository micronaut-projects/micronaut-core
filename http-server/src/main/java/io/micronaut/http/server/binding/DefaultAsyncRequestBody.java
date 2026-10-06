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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.BodyPreservingRequestWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.LifecycleHttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.exceptions.UnsupportedMediaException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@link AsyncRequestBody} of a route invocation: the body of the request the route is
 * invoked with, which it owns the one read of.
 *
 * <p>The request can be one a filter continued with, e.g. with another method: the body is that of
 * the server request it is or wraps, see {@link ServerRequestBody}.</p>
 *
 * <p>The body is read from the {@link ServerHttpRequest#byteBody()} of the request: decoded
 * through the {@code @Body} binders, outside the argument binding of the route, or moved to the
 * reader that was asked for.</p>
 *
 * <p>A filter that set the body of the mutable request it continued with, see
 * {@link ServerRequestBody#isBodySet}, replaced those bytes: a body set to {@code null} is no body,
 * and a body set to an object is only read with {@link #body(Argument)}, which converts it.</p>
 *
 * <p>A read that moves the bytes of the server request describes itself on them, see
 * {@link InternalByteBody#describeClaim}: a later reader of the request, e.g. the route after a
 * filter read the body, fails with a message that names that read. A {@link #copy() copy} reads a
 * split of the bytes instead, which leaves them to the other readers.</p>
 *
 * <p>The body and its copies can be read together, from different threads too: a read keeps what
 * its binding waits for to itself, see {@link BindingRequest}, and the reads are started one at
 * a time, since the bytes of the server request they split or move are not thread-safe.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultAsyncRequestBody implements AsyncRequestBody, AsyncHandlerBody {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAsyncRequestBody.class);
    private static final List<String> ELEMENT_MEDIA_TYPES = List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON_STREAM);
    private static final List<String> FORM_MEDIA_TYPES = List.of(MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA);

    private final HttpRequest<?> request;
    private final ServerHttpRequest<?> server;
    private final AsyncRequestBodyArgumentBinder binder;
    /**
     * The empty body of a request whose body a filter set to {@code null}, or {@code null}.
     */
    private final @Nullable ByteBody cleared;
    /**
     * Whether a filter set the body to an object, which replaces the bytes of the request.
     */
    private final boolean decoded;
    /**
     * Whether this is a copy, which reads a split of the bytes of the server request.
     */
    private final boolean copy;
    /**
     * Held while a read of the body or of one of its copies is started, and while it consumes
     * the bytes of the server request: shared by the body and its copies.
     */
    private final Object lock;

    // guarded by this
    private @Nullable String reader;
    private @Nullable Supplier<CompletionStage<Void>> release;
    private @Nullable List<DefaultAsyncRequestBody> copies;

    /**
     * @param request The request of the route
     * @param server  The server request whose bytes are the body of the request, see {@link ServerRequestBody}
     * @param binder  The binder
     */
    DefaultAsyncRequestBody(HttpRequest<?> request, ServerHttpRequest<?> server, AsyncRequestBodyArgumentBinder binder) {
        this(request, server, binder, false, new Object());
    }

    /**
     * @param request The request of the route
     * @param server  The server request whose bytes are the body of the request, see {@link ServerRequestBody}
     * @param binder  The binder
     * @param copy    Whether this is a copy, which reads a split of the bytes
     * @param lock    The lock of the body and its copies
     */
    private DefaultAsyncRequestBody(HttpRequest<?> request, ServerHttpRequest<?> server, AsyncRequestBodyArgumentBinder binder, boolean copy, Object lock) {
        this.lock = lock;
        this.request = request;
        this.server = server;
        this.binder = binder;
        this.copy = copy;
        if (ServerRequestBody.isBodySet(request)) {
            // a filter replaced the bytes of the request with the body it set
            boolean present = request.getBody().isPresent();
            this.cleared = present ? null : server.byteBodyFactory().createEmpty();
            this.decoded = present;
        } else {
            this.cleared = null;
            this.decoded = false;
        }
    }

    /**
     * @return The bytes of the body: of the server request, or none if a filter cleared the body
     */
    private ByteBody byteBody() {
        return cleared == null ? server.byteBody() : cleared;
    }

    @Override
    public boolean hasBody() {
        if (decoded) {
            return true;
        }
        OptionalLong length = byteBody().expectedLength();
        return length.isEmpty() || length.getAsLong() != 0;
    }

    @Override
    public OptionalLong expectedBodySize() {
        return decoded ? OptionalLong.empty() : byteBody().expectedLength();
    }

    @Override
    public <T> CompletionStage<@Nullable T> body(Class<T> type) {
        return body(Argument.of(type));
    }

    @Override
    public <T extends @Nullable Object> CompletionStage<@Nullable T> body(Argument<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isAsyncOrReactive()) {
            throw new IllegalArgumentException("The body cannot be read as the reactive or asynchronous type " + type.getTypeName()
                + ": read its elements one at a time with elements(), or take it with takeBody()");
        }
        if (InputStream.class.isAssignableFrom(type.getType())) {
            throw new IllegalArgumentException("The body cannot be read as an InputStream by an asynchronous handler, as reading it blocks"
                + ": take it with takeBody(), or write it to a file with transferTo()");
        }
        synchronized (lock) {
            claim("body");
            // bound like the @Body argument of a controller, with the binder of the type
            Argument<T> argument = BodyArguments.bodyArgument(type);
            CompletableFuture<@Nullable T> result = new CompletableFuture<>();
            try {
                ArgumentBinder<T, HttpRequest<?>> argumentBinder = binder.binderRegistry().findArgumentBinder(argument)
                    .orElseThrow(() -> UnsatisfiedRouteException.create(argument));
                ArgumentConversionContext<T> context = ConversionContext.of(argument, request.getLocale().orElse(null), request.getCharacterEncoding());
                // the binder waits for the body: this read waits for it, not the route, which is
                // running. What the binder waits for is kept by the request it binds from, which
                // is this read's own: another binding of the request never finds it, nor drops it
                BindingRequest<?> source = bindingSource();
                ArgumentBinder.BindingResult<T> bound = argumentBinder.bind(context, source);
                ExecutionFlow<?> waitsFor = source.waitsFor();
                waitsFor.onComplete((ignored, error) -> {
                    // the body was decoded from a split of the bytes: the read consumes them
                    consumeDecoded();
                    if (error != null) {
                        result.completeExceptionally(error);
                        return;
                    }
                    try {
                        result.complete(value(argument, context, bound));
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
                if (!result.isDone()) {
                    // the body is still arriving: a handler that completed without waiting for it
                    // aborts the read, like the other reads
                    owned(() -> {
                        abortDecode(result, waitsFor);
                        return CompletableFuture.completedStage(null);
                    });
                }
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
            // a view: the caller cannot complete or cancel the read
            return result.minimalCompletionStage();
        }
    }

    /**
     * Abort a {@link #body(Argument)} that is still waiting for the body when the handler
     * completed: the read fails as cancelled, the binder is told that the body is not needed
     * any more, and the bytes of the server request are consumed, unless this is a copy, so what
     * is left of the body is discarded as it arrives. Does nothing for a read that completed.
     *
     * @param result   The result of the read
     * @param waitsFor What the binder waits for
     */
    private void abortDecode(CompletableFuture<?> result, ExecutionFlow<?> waitsFor) {
        if (!result.completeExceptionally(new CancellationException("The body of " + request + " was closed"))) {
            return;
        }
        waitsFor.cancel();
        consumeDecoded();
    }

    @Override
    public CompletionStage<String> text() {
        synchronized (lock) {
            claimBytes("text");
            UploadContext context = uploadContext();
            return content("text", context).text(context.maxBufferSize());
        }
    }

    @Override
    public CompletionStage<String> text(int maximumBytes) {
        checkLimit(maximumBytes);
        synchronized (lock) {
            claimBytes("text");
            return content("text", uploadContext()).text(maximumBytes);
        }
    }

    @Override
    public CompletionStage<String> text(int maximumBytes, Charset charset) {
        checkLimit(maximumBytes);
        Objects.requireNonNull(charset, "charset");
        synchronized (lock) {
            claimBytes("text");
            return content("text", uploadContext()).text(maximumBytes, charset);
        }
    }

    @Override
    public CompletionStage<byte[]> bytes(int maximumBytes) {
        checkLimit(maximumBytes);
        synchronized (lock) {
            claimBytes("bytes");
            return content("bytes", uploadContext()).bytes(maximumBytes);
        }
    }

    @Override
    public CompletionStage<Void> transferTo(Path destination) {
        Objects.requireNonNull(destination, "destination");
        synchronized (lock) {
            claimBytes("transferTo");
            return content("transferTo", uploadContext()).transferTo(destination);
        }
    }

    @Override
    public <T> BodyElements<T> elements(Class<T> type) {
        return elements(Argument.of(type));
    }

    @Override
    public <T> BodyElements<T> elements(Argument<T> type) {
        Objects.requireNonNull(type, "type");
        if (type.isAsyncOrReactive()) {
            throw new IllegalArgumentException("The elements of the body cannot be read as the reactive or asynchronous type " + type.getTypeName()
                + ": an element is decoded whole, read the elements one at a time instead");
        }
        if (InputStream.class.isAssignableFrom(type.getType())) {
            throw new IllegalArgumentException("The elements of the body cannot be read as InputStreams by an asynchronous handler, as reading them blocks"
                + ": take the body with takeBody()");
        }
        synchronized (lock) {
            claimBytes("elements");
            CloseableByteBody body = readBytes("elements");
            BodyElements<T> elements = bodyElements(type, body);
            owned(elements::closeAsync);
            return elements;
        }
    }

    @Override
    public CompletionStage<FormData> form() {
        synchronized (lock) {
            return startForm();
        }
    }

    private CompletionStage<FormData> startForm() {
        if (decoded) {
            // a filter set the body to an object: the form it set, or one it converts to
            CompletableFuture<@Nullable FormData> replaced = FormBinding.replacedForm(request, binder.conversionService);
            FormData form = replaced == null ? null : replaced.getNow(null);
            if (form != null) {
                claim("form");
                return CompletableFuture.completedStage(form);
            }
        }
        claimBytes("form");
        try {
            if (cleared != null) {
                checkForm();
                // no body: a form without fields
                return CompletableFuture.completedFuture(new DefaultFormData(Map.of(), Map.of(), binder.conversionService));
            }
            FormCapableHttpRequest<?> formRequest = formRequest();
            // the one form of the request, shared with the FormData, FileUpload and text field
            // arguments of the route and of the filters, and released when the request ends
            FormBinding binding = FormBinding.of(formRequest);
            if (copy) {
                // a copy leaves the body to the other readers of the request: a form this read
                // starts is decoded from a split of the bytes, so the route can still read the
                // body in any way, and is the form of the request, which the form arguments
                // share. It is not cancelled with the handler, the request releases it
                return binding.form(binder.formFactory(), binder.conversionService, () -> copiedFields(formRequest)).minimalCompletionStage();
            }
            // the form is read by the handler, outside the argument binding of the route, which
            // it does not delay: the route of a filter that read it does not wait for it
            FormDataArgumentBinder.Collection started = binding.startDetachedForm(binder.formFactory(), binder.conversionService);
            CompletableFuture<FormData> form = binding.form(binder.formFactory(), binder.conversionService);
            // the form stays the one of the request, but this read consumes the body: the
            // form arguments and the readers of the bytes that come later fail
            describeRead(server.byteBody(), "form");
            if (started != null) {
                // this read started the form, and consumed the body: no other argument can
                // share it, so its collection is cancelled when the handler completed, like
                // the other reads. A form an argument started before, e.g. a FormData of a
                // filter, is shared: the request releases it when it ends
                owned(() -> {
                    started.cancel();
                    return CompletableFuture.completedStage(null);
                });
            }
            return form.minimalCompletionStage();
        } catch (Throwable e) {
            return CompletableFuture.failedStage(e);
        }
    }

    @Override
    public FormParts parts() {
        synchronized (lock) {
            claimBytes("parts");
            if (cleared != null) {
                checkForm();
                // no body: a form without parts
                return NoFormParts.INSTANCE;
            }
            FormCapableHttpRequest<?> formRequest = formRequest();
            UploadContext context = UploadContext.of(binder.formFactory(), formRequest, request.getCharacterEncoding());
            // the fields are decoded when the first part is asked for: a copy decodes a split of the
            // bytes, which leaves the whole body to the other readers of the request
            DefaultFormParts parts = new DefaultFormParts(() -> {
                synchronized (lock) {
                    if (copy) {
                        return copiedFields(formRequest);
                    }
                    Publisher<RawFormField> fields = formRequest.getRawFormFields();
                    // the fields claimed the bytes
                    describeRead(server.byteBody(), "parts");
                    return fields;
                }
            }, context);
            owned(parts::closeAsync);
            return parts;
        }
    }

    @Override
    public CloseableByteBody takeBody() {
        synchronized (lock) {
            claimBytes("takeBody");
            return readBytes("takeBody");
        }
    }

    @Override
    public CompletionStage<Void> discardBody() {
        synchronized (lock) {
            claim("discardBody");
            if (!decoded && !copy) {
                // a copy has not split the bytes it did not read: nothing to discard
                readBytes("discardBody").close();
            }
        }
        return CompletableFuture.completedStage(null);
    }

    @Override
    public AsyncRequestBody copy() {
        DefaultAsyncRequestBody copy = new DefaultAsyncRequestBody(request, server, binder, true, lock);
        synchronized (this) {
            if (copies == null) {
                copies = new ArrayList<>(1);
            }
            // the reads of the copy are released with the body, when the handler completed
            copies.add(copy);
        }
        return copy;
    }

    /**
     * Release what the reader of the body left open when the handler completed: the parts of a
     * form, the elements of the body, or an operation on the body that is still running, and
     * what the readers of its copies left open.
     *
     * @return Completes when released, exceptionally when releasing failed
     */
    @Override
    public CompletionStage<Void> releaseBody() {
        Supplier<CompletionStage<Void>> r;
        List<DefaultAsyncRequestBody> c;
        synchronized (this) {
            r = release;
            c = copies == null ? List.of() : List.copyOf(copies);
        }
        ReleasableRequestBody released = r == null ? null : r::get;
        for (DefaultAsyncRequestBody copy : c) {
            released = released == null ? copy : ReleasableRequestBody.both(released, copy);
        }
        return released == null ? CompletableFuture.completedStage(null) : released.releaseBody();
    }

    @Override
    public String toString() {
        return "the body of " + request;
    }

    /**
     * The request the body is decoded from by the {@code @Body} binders, one for each read, which
     * keeps what the binder waits for, see {@link BindingRequest}. The decoded body is the
     * handler's, not a body of the request, so a server request is bound through a view, which the
     * binders do not keep the decoded body in; {@link HttpRequest#getBody()} of the request does
     * not change. A request whose body a filter set to an object is bound from that object.
     *
     * @return The request to bind the body from
     */
    private BindingRequest<?> bindingSource() {
        if (!decoded && request instanceof ServerHttpRequest<?>) {
            return new BindingView<>(request);
        }
        return new BindingRequest<>(request);
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
        if (!decoded && cleared == null) {
            // another reader of the request consumed the bytes, e.g. a filter
            String consumed = InternalByteBody.claimDescription(server.byteBody());
            if (consumed != null) {
                throw new IllegalStateException(consumed);
            }
        }
        reader = name;
    }

    /**
     * Claim the bytes of the body for a reader, which a filter did not replace with an object.
     *
     * @param name The method that reads the body
     */
    private void claimBytes(String name) {
        if (decoded) {
            throw new IllegalStateException("The body of the request cannot be read with " + name
                + "(): a filter replaced the body with a decoded object, read it with body(Type)");
        }
        claim(name);
    }

    /**
     * The bytes a read owns: the empty body of a request whose body a filter cleared, a split of
     * the bytes of the server request for a copy, or else those bytes, moved to the read, which
     * describes itself on them for the later readers of the request.
     *
     * @param name The method that reads the body
     * @return The bytes
     */
    private CloseableByteBody readBytes(String name) {
        if (cleared != null) {
            return cleared.move();
        }
        if (copy) {
            return split();
        }
        ByteBody bytes = server.byteBody();
        CloseableByteBody moved = bytes.move();
        describeRead(bytes, name);
        return moved;
    }

    /**
     * The form fields of a copy: decoded from a split of the bytes as they arrive, for its parts,
     * which decode the form a second time, or for the form of the request when the copy starts it.
     * The body keeps those bytes for its other readers, so a copy is held to the buffer limit of
     * the server as a whole.
     *
     * <p>A request that cannot decode other bytes than its own refuses the split, e.g. with the
     * default {@link FormCapableHttpRequest#getRawFormFields(ByteBody)}: the split is closed.</p>
     *
     * @param formRequest The request, with a form body
     * @return The fields
     */
    private Publisher<RawFormField> copiedFields(FormCapableHttpRequest<?> formRequest) {
        CloseableByteBody split = split();
        try {
            return formRequest.getRawFormFields(split);
        } catch (Throwable e) {
            // no publisher took the split
            split.close();
            throw e;
        }
    }

    /**
     * Consume the bytes of the server request once {@link #body(Argument)} decoded a split of them,
     * unless this is a copy: the read owns the body, like the other reads.
     */
    private void consumeDecoded() {
        if (decoded || cleared != null || copy) {
            return;
        }
        synchronized (lock) {
            ByteBody bytes = server.byteBody();
            try {
                bytes.move().close();
            } catch (IllegalStateException e) {
                // e.g. a binder that read the bytes of the request itself
            }
            describeRead(bytes, "body");
        }
    }

    /**
     * A split of the bytes of the server request, for a copy: the bytes the copy reads are
     * buffered for the other readers of the request, e.g. the route after a filter. The split
     * reads as fast as the faster of the two, since the route runs only once the filter completed.
     *
     * <p>The split is limited to what the body can keep for its other readers, the buffer limit
     * of the server: the body only limits the bytes it keeps, which it drops once they exceed the
     * limit, so a reader that streams the split, e.g. the elements or a transfer to a file,
     * would read a body of any size and leave nothing of it to the route. A split that reads more
     * than the limit fails with a {@link io.micronaut.http.exceptions.ContentLengthExceededException}
     * instead. The limit counts the bytes the split reads, not the bytes that are buffered for it.</p>
     *
     * @return The split
     */
    private CloseableByteBody split() {
        CloseableByteBody split = server.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
        try {
            int limit = uploadContext().maxBufferSize();
            long expected = split.expectedLength().orElse(-1);
            if (expected >= 0 && expected <= limit) {
                return split;
            }
            return server.byteBodyFactory().adapt(InternalByteBody.toUnbufferedReadBufferPublisher(split),
                new BodySizeLimits(limit, Integer.MAX_VALUE), null, null);
        } catch (Throwable e) {
            split.close();
            throw e;
        }
    }

    /**
     * Describe a read that consumed the bytes of the server request on them, for a later reader.
     *
     * @param bytes The bytes of the server request
     * @param name  The method that read the body
     */
    private static void describeRead(ByteBody bytes, String name) {
        InternalByteBody.describeClaim(bytes, "The body of the request was already read with " + name
            + "() of an AsyncRequestBody, e.g. by a filter, which consumes it: read it with copy() in the filter to leave it for the route, or bind it with @Body");
    }

    /**
     * Keep what the reader of the body must release when the handler completes, see
     * {@link #releaseBody()}, and when the request ends, in case it was not: e.g. the body of a
     * route that failed before it was invoked. Releasing again does nothing more. A failure to
     * release when the request ends is logged: the response was written.
     */
    private void owned(Supplier<CompletionStage<Void>> closeAsync) {
        synchronized (this) {
            release = closeAsync;
        }
        if (server instanceof LifecycleHttpRequest<?> lifecycle) {
            lifecycle.addDisposalResource(() -> {
                CompletionStage<Void> released;
                try {
                    released = closeAsync.get();
                } catch (Throwable e) {
                    released = CompletableFuture.failedStage(e);
                }
                released.whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOG.warn("Failed to release what the read of {} left open when the request ended", this, error);
                    }
                });
            });
        }
    }

    /**
     * @return The context of the body: the text is in the charset of the request of the route,
     * like its content type
     */
    private UploadContext uploadContext() {
        return UploadContext.of(binder.formFactory(), server, request.getCharacterEncoding());
    }

    /**
     * The body, moved to content read like a form field: in memory with a limit, or to a file.
     */
    private UploadContent content(String name, UploadContext context) {
        StreamingUploadContent content = StreamingUploadContent.requestBody(readBytes(name), request.getContentType().orElse(null), context);
        owned(content::closeAsync);
        return content;
    }

    /**
     * Check that the request is a form, by its content type, e.g. when a filter cleared its body.
     */
    private void checkForm() {
        if (!FormBinding.isForm(request)) {
            throw new UnsupportedMediaException(String.valueOf(request.getContentType().orElse(null)), FORM_MEDIA_TYPES);
        }
    }

    private FormCapableHttpRequest<?> formRequest() {
        if (server instanceof FormCapableHttpRequest<?> formRequest && formRequest.hasFormBody()) {
            return formRequest;
        }
        throw new UnsupportedMediaException(String.valueOf(request.getContentType().orElse(null)), FORM_MEDIA_TYPES);
    }

    /**
     * The elements of the body: read through the piece reader of the chunked reader, without
     * Reactor, or through its publisher. A body that cannot be read as elements fails the first
     * read, like a failure to decode it.
     */
    private <T> BodyElements<T> bodyElements(Argument<T> type, CloseableByteBody body) {
        @Nullable MediaType contentType;
        ChunkedMessageBodyReader<T> chunked;
        PieceReader<T> pieceReader;
        try {
            contentType = request.getContentType().orElse(null);
            chunked = chunkedReader(type, contentType);
            // an element is decoded in memory: it is limited like buffered content. The body is
            // streamed without being held, so the body is not, nor the bytes that arrived before
            // it is read
            pieceReader = chunked.openPieceReader(type, contentType, request.getHeaders(), uploadContext().maxBufferSize());
        } catch (RuntimeException e) {
            return new PublisherBodyElements<>(() -> {
                throw e;
            }, body::close);
        }
        if (pieceReader != null) {
            return new ByteBodyElements<>(body, pieceReader, Function.identity());
        }
        return new PublisherBodyElements<>(() -> elementPublisher(type, contentType, chunked, body), body::close);
    }

    private <T> ChunkedMessageBodyReader<T> chunkedReader(Argument<T> type, @Nullable MediaType contentType) {
        if (contentType == null || !isJson(contentType)) {
            throw new UnsupportedMediaException(String.valueOf(contentType), ELEMENT_MEDIA_TYPES);
        }
        MessageBodyReader<T> elementReader = binder.bodyHandlerRegistry().findReader(type, List.of(contentType)).orElse(null);
        if (!(elementReader instanceof ChunkedMessageBodyReader<T> chunked)) {
            // a JSON body: the media type is supported, what is missing is the reader, e.g. on a
            // server that is not the Netty server, without micronaut-http-netty
            throw new UnsupportedOperationException("Reading the elements of a JSON body [" + contentType
                + "] needs a chunked JSON message body reader, which micronaut-http-netty provides: add it to the runtime classpath");
        }
        return chunked;
    }

    /**
     * The elements of a chunked reader that only reads a publisher.
     */
    private <T> Publisher<? extends T> elementPublisher(Argument<T> type, @Nullable MediaType contentType, ChunkedMessageBodyReader<T> chunked, CloseableByteBody body) {
        Publisher<ByteBuffer<?>> bytes = Flux.from(InternalByteBody.toUnbufferedReadBufferPublisher(body))
            .doOnDiscard(ReadBuffer.class, ReadBuffer::close)
            .map(rb -> {
                try (rb) {
                    return rb.toByteBuffer();
                }
            });
        return chunked.readChunked(type, contentType, request.getHeaders(), bytes, uploadContext().maxBufferSize());
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
            throw new ConversionErrorException(argument, errors.getFirst());
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
            return binder.conversionService.convertRequired(value, argument);
        }
        if (argument.isOptional()) {
            return (T) Optional.empty();
        }
        if (argument.isNullable()) {
            return null;
        }
        throw UnsatisfiedRouteException.create(argument);
    }

    /**
     * The request one {@link #body(Argument)} is bound from by the {@code @Body} binders: the
     * request of the route, which also keeps what the binder waits for, see
     * {@link BasicHttpAttributes#addRouteWaitsFor}. The read waits for it, not the route, and no
     * attribute of the request holds it: the reads of the body and of its copies that are in
     * flight together, and the other bindings of the request, do not see or replace the
     * conditions of one another. It never replaces the body.
     *
     * @param <B> The body type
     */
    private static class BindingRequest<B> extends HttpRequestWrapper<B> implements BodyPreservingRequestWrapper, BasicHttpAttributes.DetachedBinding {
        private @Nullable ExecutionFlow<?> waitsFor;

        BindingRequest(HttpRequest<B> request) {
            super(request);
        }

        @Override
        public final void addWaitsFor(ExecutionFlow<?> flow) {
            ExecutionFlow<?> existing = waitsFor;
            waitsFor = existing == null ? flow : existing.then(() -> flow);
        }

        /**
         * @return What the binding waits for
         */
        final ExecutionFlow<?> waitsFor() {
            ExecutionFlow<?> flow = waitsFor;
            return flow == null ? ExecutionFlow.empty() : flow;
        }

        @Override
        public Optional<Object> getAttribute(CharSequence name) {
            return getDelegate().getAttribute(name);
        }

        @Override
        public Charset getCharacterEncoding() {
            return getDelegate().getCharacterEncoding();
        }

        @Override
        public Optional<MediaType> getContentType() {
            return getDelegate().getContentType();
        }

        @Override
        public long getContentLength() {
            return getDelegate().getContentLength();
        }
    }

    /**
     * A view of a server request for the {@code @Body} binders: they read the bytes of the server
     * request it wraps, see {@link ServerRequestBody}, and keep no decoded body in it. It never
     * replaces the body, so the bytes are found without decoding the body of the request.
     *
     * @param <B> The body type
     */
    private static final class BindingView<B> extends BindingRequest<B> {

        BindingView(HttpRequest<B> request) {
            super(request);
        }

        @Override
        public Optional<B> getBody() {
            // the body is read from the bytes, not from a body a binder decoded before, e.g. for a filter
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
    }

    /**
     * The parts of a form without a body.
     */
    static final class NoFormParts implements FormParts {
        static final NoFormParts INSTANCE = new NoFormParts();

        @Override
        public CompletionStage<Void> forEach(Function<? super FormPart, ? extends CompletionStage<?>> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            return CompletableFuture.completedStage(null);
        }

        @Override
        public CompletionStage<Boolean> part(String name, Function<? super FormPart, ? extends CompletionStage<?>> consumer) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(consumer, "consumer");
            return CompletableFuture.completedStage(false);
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedStage(null);
        }

        @Override
        public void close() {
            // nothing to release: there are no parts
        }
    }
}
