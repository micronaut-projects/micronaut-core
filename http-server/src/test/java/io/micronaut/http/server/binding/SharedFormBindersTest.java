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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.MultipartBody;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

/** Shared legacy binders exercised without a Netty request or Netty server dependency. */
class SharedFormBindersTest {
    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void immediateFormTransformationReturnsAnImperativeFlow() {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello")) {
            ServerBodyAnnotationBinder<Map> binder = context.getBean(ServerBodyAnnotationBinder.class);
            var result = binder.transform(request, request, ConversionContext.of(Map.class),
                BODIES.copyOf("form", StandardCharsets.UTF_8));
            org.junit.jupiter.api.Assertions.assertInstanceOf(io.micronaut.core.execution.ImperativeExecutionFlow.class, result);
            assertEquals(Map.of("message", "hello"), result.tryCompleteValue().orElseThrow());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void decoderCreationFailureClosesTheBufferedBodyBeforeHandoff() {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello");
             var bytes = BODIES.copyOf("form", StandardCharsets.UTF_8)) {
            request.failDecoderCreation = true;
            ServerBodyAnnotationBinder<Map> binder = context.getBean(ServerBodyAnnotationBinder.class);
            assertThrows(UnsupportedOperationException.class, () -> binder.transform(request, request,
                ConversionContext.of(Map.class), bytes));
            assertThrows(IllegalStateException.class, bytes::move);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void fullFormBindingWaitsForDelayedDecoder(boolean wrapped) throws Exception {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello")) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            HttpRequest<?> source = source(request, wrapped);
            ServerBodyAnnotationBinder<Map> binder = context.getBean(ServerBodyAnnotationBinder.class);
            var result = binder.bindFullBody(ConversionContext.of(Map.class), source);
            var waiting = BasicHttpAttributes.getRouteWaitsFor(source).toCompletableFuture();
            assertTrue(((PendingRequestBindingResult<?>) result).isPending());
            assertFalse(waiting.isDone());
            request.delayed.tryEmitNext(request.field);
            request.delayed.tryEmitComplete();
            waiting.get(10, TimeUnit.SECONDS);
            assertFalse(((PendingRequestBindingResult<?>) result).isPending());
            assertEquals(Map.of("message", "hello"), result.getValue().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"future", "publisher"})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void asynchronousBodyBindingsWaitForDelayedFormDecoder(String kind) throws Exception {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello")) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            var bodyBinder = context.getBean(ServerBodyAnnotationBinder.class);
            CompletableFuture<?> future;
            HttpRequest<?> wrapped = new HttpRequestWrapper<>(request);
            if (kind.equals("future")) {
                Argument argument = Argument.of(CompletableFuture.class, "body", Argument.of(Map.class));
                future = (CompletableFuture<?>) new CompletableFutureBodyBinder(bodyBinder)
                    .bind(ConversionContext.of(argument), wrapped).getValue().orElseThrow();
            } else {
                Argument argument = Argument.of(Publisher.class, "body", Argument.of(Map.class));
                Publisher<?> publisher = (Publisher<?>) new PublisherBodyBinder(bodyBinder)
                    .bind(ConversionContext.of(argument), wrapped).getValue().orElseThrow();
                future = Flux.from(publisher).single().toFuture();
            }
            assertFalse(future.isDone());
            request.delayed.tryEmitNext(request.field);
            request.delayed.tryEmitComplete();
            assertEquals(Map.of("message", "hello"), future.get(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"future", "publisher"})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cancellingAsyncFormBindingCancelsTheDecoder(String kind) {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello")) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            var bodyBinder = context.getBean(ServerBodyAnnotationBinder.class);
            CompletableFuture<?> future;
            if (kind.equals("future")) {
                Argument argument = Argument.of(CompletableFuture.class, "body", Argument.of(Map.class));
                future = (CompletableFuture<?>) new CompletableFutureBodyBinder(bodyBinder)
                    .bind(ConversionContext.of(argument), request).getValue().orElseThrow();
            } else {
                Argument argument = Argument.of(Publisher.class, "body", Argument.of(Map.class));
                Publisher<?> publisher = (Publisher<?>) new PublisherBodyBinder(bodyBinder)
                    .bind(ConversionContext.of(argument), request).getValue().orElseThrow();
                future = Flux.from(publisher).single().toFuture();
            }
            assertEquals(1, request.delayed.currentSubscriberCount());
            future.cancel(false);
            assertEquals(0, request.delayed.currentSubscriberCount());
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cancellingWhileAFieldIsBufferingDoesNotConvertItsLateBytes() {
        var streaming = BODIES.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytes) {
                // Buffering does not impose a separate demand limit.
            }

        });
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello");
             RawFormField slow = new RawFormField(request.field.metadata(), streaming.rootBody())) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            var bodyBinder = context.getBean(ServerBodyAnnotationBinder.class);
            Argument argument = Argument.of(CompletableFuture.class, "body", Argument.of(Map.class));
            var future = (CompletableFuture<?>) new CompletableFutureBodyBinder(bodyBinder)
                .bind(ConversionContext.of(argument), request).getValue().orElseThrow();
            request.delayed.tryEmitNext(slow);
            request.delayed.tryEmitComplete();
            assertFalse(future.isDone());
            future.cancel(false);
            // Full-body buffering only supports cancellation as a hint. The active field
            // may still complete; its mapped available body must be closed, not converted.
            streaming.sharedBuffer().add(BODIES.readBufferFactory().copyOf("late", StandardCharsets.UTF_8));
            streaming.sharedBuffer().complete();
            assertTrue(future.isCancelled());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cancellingDiscardsFieldsQueuedByAForeignDecoder(boolean wrappedPublisher) {
        var streaming = BODIES.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            public void onBytesConsumed(long bytes) {
                // Keep the first field pending while the decoder queues the second field.
            }
        });
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello");
             RawFormField slow = new RawFormField(request.field.metadata(), streaming.rootBody());
             RawFormField queued = new RawFormField(request.field.metadata(), BODIES.copyOf("queued", StandardCharsets.UTF_8))) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            request.wrapPublisher = wrappedPublisher;
            var bodyBinder = context.getBean(ServerBodyAnnotationBinder.class);
            Argument argument = Argument.of(CompletableFuture.class, "body", Argument.of(Map.class));
            var future = (CompletableFuture<?>) new CompletableFutureBodyBinder(bodyBinder)
                .bind(ConversionContext.of(argument), request).getValue().orElseThrow();
            request.delayed.tryEmitNext(slow);
            request.delayed.tryEmitNext(queued);
            future.cancel(false);
            assertEquals(0, request.delayed.currentSubscriberCount());
            assertThrows(IllegalStateException.class, () -> ((AvailableByteBody) queued.byteBody()).toReadBuffer());
            streaming.sharedBuffer().complete();
        }
    }

    @Test
    void delayedConversionRunsWithTheBoundRequestContext() throws Exception {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello")) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            HttpRequest<?> wrapped = new HttpRequestWrapper<>(request);
            AtomicBoolean converted = new AtomicBoolean();
            var binder = new ServerBodyAnnotationBinder<Map>(ConversionService.SHARED,
                context.getBean(MessageBodyHandlerRegistry.class), () -> context.getBean(FormFactory.class)) {
                @Override
                protected void cacheDecodedBody(HttpRequest<?> bound, ServerHttpRequest<?> server, @Nullable Object value) {
                    assertSame(wrapped, ServerRequestContext.currentRequest().orElseThrow());
                    converted.set(true);
                }
            };
            binder.bindFullBody(ConversionContext.of(Map.class), wrapped);
            CompletableFuture.runAsync(() -> {
                request.delayed.tryEmitNext(request.field);
                request.delayed.tryEmitComplete();
            }).get(10, TimeUnit.SECONDS);
            BasicHttpAttributes.getRouteWaitsFor(wrapped).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(converted.get());
            assertTrue(ServerRequestContext.currentRequest().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void delayedFormDecoderFailureFailsTheRouteWait(boolean wrapped) {
        for (Throwable failure : new Throwable[]{new IllegalArgumentException("decode failure"),
            new HttpStatusException(HttpStatus.BAD_REQUEST, "malformed multipart"),
            new ContentLengthExceededException("form limit")}) {
            try (ApplicationContext context = ApplicationContext.run();
                 FormRequest request = new FormRequest("message", null, "hello")) {
                request.delayed = Sinks.many().unicast().onBackpressureBuffer();
                HttpRequest<?> source = source(request, wrapped);
                ServerBodyAnnotationBinder<Map> binder = context.getBean(ServerBodyAnnotationBinder.class);
                binder.bindFullBody(ConversionContext.of(Map.class), source);
                var waiting = BasicHttpAttributes.getRouteWaitsFor(source).toCompletableFuture();
                request.delayed.tryEmitError(failure);
                ExecutionException error = assertThrows(ExecutionException.class, () -> waiting.get(10, TimeUnit.SECONDS));
                assertSame(failure, error.getCause());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void delayedFormDecoderPreservesEmptyFormsAndRepeatedFields(boolean empty) throws Exception {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello");
             RawFormField second = new RawFormField(request.field.metadata(), BODIES.copyOf("again", StandardCharsets.UTF_8))) {
            request.delayed = Sinks.many().unicast().onBackpressureBuffer();
            ServerBodyAnnotationBinder<Map> binder = context.getBean(ServerBodyAnnotationBinder.class);
            var result = binder.bindFullBody(ConversionContext.of(Map.class), request);
            var waiting = BasicHttpAttributes.getRouteWaitsFor(request).toCompletableFuture();
            if (!empty) {
                request.delayed.tryEmitNext(request.field);
                request.delayed.tryEmitNext(second);
            }
            request.delayed.tryEmitComplete();
            waiting.get(10, TimeUnit.SECONDS);
            assertEquals(empty ? Map.of() : Map.of("message", List.of("hello", "again")), result.getValue().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void namedTextPartUsesTheFormCapability(boolean wrapped) {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("message", null, "hello")) {
            FormFactory factory = context.getBean(FormFactory.class);
            var binder = new PartUploadAnnotationBinder<String>(ConversionService.SHARED,
                new CompletedFileUploadBinder(() -> factory),
                new PublisherPartUploadBinder(ConversionService.SHARED, () -> factory), () -> factory);
            var result = binder.bind(ConversionContext.of(Argument.of(String.class, "message")), source(request, wrapped));
            assertEquals("hello", result.getValue().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void publisherUploadUsesTheFormCapability(boolean wrapped) throws Exception {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("file", "file.txt", "hello")) {
            FormFactory factory = context.getBean(FormFactory.class);
            PublisherPartUploadBinder binder = new PublisherPartUploadBinder(ConversionService.SHARED, () -> factory);
            Argument argument = Argument.of(Publisher.class, "file", Argument.of(CompletedFileUpload.class));
            Publisher<?> publisher = (Publisher<?>) binder.bind(ConversionContext.of(argument), source(request, wrapped)).getValue().orElseThrow();
            var result = Flux.from(publisher).collectList().toFuture();
            factory.getOrCreateCompleter(request).start();
            var uploads = result.get(10, TimeUnit.SECONDS);
            assertEquals(1, uploads.size());
            try (CompletedFileUpload upload = (CompletedFileUpload) uploads.getFirst()) {
                assertEquals("hello", new String(upload.getBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completedUploadUsesTheFormCapability(boolean wrapped) throws Exception {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("file", "file.txt", "hello")) {
            FormFactory factory = context.getBean(FormFactory.class);
            CompletedFileUploadBinder binder = new CompletedFileUploadBinder(() -> factory);
            var result = binder.bind(ConversionContext.of(Argument.of(CompletedFileUpload.class, "file")), source(request, wrapped));
            factory.getOrCreateCompleter(request).start();
            assertFalse(((PendingRequestBindingResult<?>) result).isPending());
            try (CompletedFileUpload upload = result.getValue().orElseThrow()) {
                assertEquals("file.txt", upload.getFilename());
                assertEquals("hello", new String(upload.getBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void streamingUploadUsesTheFormCapability(boolean wrapped) {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("file", "file.txt", "hello")) {
            StreamingFileUploadBinder binder = new StreamingFileUploadBinder(() -> context.getBean(FormFactory.class));
            var result = binder.bind(ConversionContext.of(Argument.of(StreamingFileUpload.class, "file")), source(request, wrapped));
            context.getBean(FormFactory.class).getOrCreateCompleter(request).start();
            try (StreamingFileUpload upload = result.getValue().orElseThrow()) {
                assertEquals("file.txt", upload.getFilename());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void multipartBodyUsesTheFormCapability(boolean wrapped) {
        try (ApplicationContext context = ApplicationContext.run();
             FormRequest request = new FormRequest("file", "file.txt", "hello")) {
            MultipartBodyArgumentBinder binder = new MultipartBodyArgumentBinder(() -> context.getBean(FormFactory.class));
            MultipartBody body = binder.bind(ConversionContext.of(MultipartBody.class), source(request, wrapped)).getValue().orElseThrow();
            var parts = Flux.from(body).collectList().block();
            assertNotNull(parts);
            assertEquals(1, parts.size());
            parts.forEach(part -> part.closeAsync(Runnable::run));
        }
    }

    private static HttpRequest<?> source(FormRequest request, boolean wrapped) {
        return wrapped ? new HttpRequestWrapper<>(request) : request;
    }

    private static final class FormRequest extends HttpRequestWrapper<Object> implements FormCapableHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body = BODIES.copyOf("form", StandardCharsets.UTF_8);
        private final RawFormField field;
        private final List<Runnable> disposal = new ArrayList<>();
        private Sinks.Many<RawFormField> delayed;
        private boolean failDecoderCreation;
        private boolean wrapPublisher;

        FormRequest(String name, @Nullable String filename, String value) {
            super(HttpRequest.POST("/form", null).contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
            field = new RawFormField(new FormFieldMetadata(name, filename, MediaType.TEXT_PLAIN_TYPE), BODIES.copyOf(value, StandardCharsets.UTF_8));
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return Flux.just(field);
        }

        @Override
        public Publisher<RawFormField> getRawFormFields(ByteBody bytes) {
            if (failDecoderCreation) {
                throw new UnsupportedOperationException("decoder creation");
            }
            ((CloseableByteBody) bytes).close();
            Publisher<RawFormField> source = delayed == null ? Flux.just(field) : delayed.asFlux();
            return wrapPublisher ? subscriber -> source.subscribe(subscriber) : source;
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public void addDisposalResource(Runnable dispose) {
            disposal.add(dispose);
        }

        @Override
        public void close() {
            disposal.forEach(Runnable::run);
            field.close();
            body.close();
        }
    }
}
