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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import io.micronaut.http.server.multipart.FormFactory;
import org.openjdk.jmh.annotations.*;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Compares the old buffered form transformation with native asynchronous transformation.
 * Includes field creation and conversion, but not HTTP transport or form-byte parsing.
 * The delayed case has no old counterpart: the old implementation rejected delayed decoding.
 */
@Internal
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class BufferedFormBindingBenchmark {
    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    @Param({"1", "16"})
    public int fields;
    private ApplicationContext context;
    private ServerBodyAnnotationBinder<Map> binder;
    private ExecutorService executor;

    /** Initialize outside measurement. */
    @Setup
    @SuppressWarnings("unchecked")
    public void setup() {
        context = ApplicationContext.run();
        binder = new ServerBodyAnnotationBinder<>(ConversionService.SHARED,
            context.getBean(MessageBodyHandlerRegistry.class), () -> context.getBean(FormFactory.class));
        executor = Executors.newSingleThreadExecutor();
    }

    /** Release outside measurement. */
    @TearDown
    public void close() {
        executor.shutdown();
        context.close();
    }

    /** @return The converted form */
    @Benchmark
    public Object previousSynchronousForm() {
        try (FormRequest request = new FormRequest(false)) {
            // Same no-reader lookup and buffered-byte handoff as the shared transform.
            binder.bodyHandlerRegistry.findReader(Argument.of(Map.class), List.of(MediaType.MULTIPART_FORM_DATA_TYPE));
            List<RawFormField> decoded = new ArrayList<>();
            Map<String, List<CloseableByteBody>> bodies = new LinkedHashMap<>();
            request.getRawFormFields(BODIES.copyOf("form", StandardCharsets.UTF_8)).subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(RawFormField field) {
                    decoded.add(field);
                }

                @Override
                public void onError(Throwable error) {
                    throw new IllegalStateException(error);
                }

                @Override
                public void onComplete() {
                    // This baseline intentionally requires an immediately completed decoder.
                }
            });
            for (RawFormField field : decoded) {
                bodies.computeIfAbsent(field.metadata().name(), k -> new ArrayList<>(1)).add(field.byteBody());
            }
            return ConversionService.SHARED.convert(FormRouteCompleter.mapForGetBody(bodies, StandardCharsets.UTF_8),
                ConversionContext.of(Map.class)).orElseThrow();
        }
    }

    /** @return The converted form */
    @Benchmark
    public Object nativeImmediateForm() {
        return nativeForm(false);
    }

    /** @return The converted form after asynchronous decoding */
    @Benchmark
    public Object nativeDelayedForm() {
        return nativeForm(true);
    }

    private Object nativeForm(boolean delayed) {
        try (FormRequest request = new FormRequest(delayed)) {
            return binder.transform(request, request, ConversionContext.of(Map.class), BODIES.copyOf("form", StandardCharsets.UTF_8))
                .toCompletableFuture().join().orElseThrow();
        }
    }

    @Internal
    private final class FormRequest extends HttpRequestWrapper<Object> implements FormCapableHttpRequest<Object>, AutoCloseable {
        private final List<RawFormField> parts = new ArrayList<>();
        private final boolean delayed;

        FormRequest(boolean delayed) {
            super(HttpRequest.POST("/form", null).contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
            this.delayed = delayed;
            for (int i = 0; i < fields; i++) {
                parts.add(new RawFormField(new FormFieldMetadata("field" + i, null, MediaType.TEXT_PLAIN_TYPE),
                    BODIES.copyOf("value", StandardCharsets.UTF_8)));
            }
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return Flux.fromIterable(parts);
        }

        @Override
        public Publisher<RawFormField> getRawFormFields(ByteBody bytes) {
            ((CloseableByteBody) bytes).close();
            Flux<RawFormField> publisher = Flux.fromIterable(parts);
            return delayed ? publisher.subscribeOn(Schedulers.fromExecutor(executor)) : publisher;
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public ByteBody byteBody() {
            throw new UnsupportedOperationException("Only transformation is measured");
        }

        @Override
        public void addDisposalResource(Runnable resource) {
            throw new UnsupportedOperationException("Only transformation is measured");
        }

        @Override
        public void close() {
            parts.forEach(RawFormField::close);
        }
    }
}
