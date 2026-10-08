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
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.multipart.FormFactory;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Production collection versus a sequential Reactor control with matching reader lookup,
 * request setup, conversion, context and future adapter. Does not parse HTTP or form bytes.
 */
@Internal
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class FormAggregationBenchmark {
    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Param({"native", "reactor"})
    public String implementation;
    @Param({"1", "16"})
    public int fields;
    @Param({"false", "true"})
    public boolean delayed;

    private ApplicationContext application;
    private ServerBodyAnnotationBinder<Map> binder;
    private ExecutorService executor;
    private Scheduler scheduler;

    /** Initialize and verify representative result parity outside measurement. */
    @Setup
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void setup() {
        application = ApplicationContext.run();
        var registry = application.getBean(MessageBodyHandlerRegistry.class);
        if (registry.findReader(Argument.of(Map.class), List.of(MediaType.MULTIPART_FORM_DATA_TYPE)).isPresent()) {
            throw new IllegalStateException("The fixture requires the form aggregation fallback");
        }
        BeanProvider<FormFactory> factory = () -> application.getBean(FormFactory.class);
        ServerBodyAnnotationBinder<Map> nativeBinder = new ServerBodyAnnotationBinder<>(ConversionService.SHARED, registry, factory);
        ServerBodyAnnotationBinder<Map> reactorBinder = new ReactorFormBinder(registry, factory);
        binder = implementation.equals("native") ? nativeBinder : reactorBinder;
        executor = Executors.newSingleThreadExecutor();
        scheduler = Schedulers.fromExecutor(executor);
        for (int count : new int[]{0, 1, 16}) {
            for (boolean repeated : new boolean[]{false, true}) {
                if (!run(nativeBinder, count, repeated).equals(run(reactorBinder, count, repeated))) {
                    throw new IllegalStateException("Collector results differ");
                }
            }
        }
    }

    /** Release decoder executor and context outside measurement. */
    @TearDown
    public void close() {
        scheduler.dispose();
        executor.shutdown();
        application.close();
    }

    /** @return The converted form using the same result adapter for both implementations */
    @Benchmark
    public Object aggregate() {
        return run(binder, fields, false);
    }

    private Object run(ServerBodyAnnotationBinder<Map> selected, int count, boolean repeated) {
        try (FormRequest request = new FormRequest(count, repeated)) {
            var flow = selected.transform(request, request, ConversionContext.of(Map.class),
                BODIES.copyOf("form", StandardCharsets.UTF_8));
            var future = flow.toCompletableFuture();
            future.whenComplete((value, error) -> {
                if (future.isCancelled()) {
                    flow.cancel();
                }
            });
            return future.join().orElseThrow();
        }
    }

    /** @param args Unused; run parity checks without JMH */
    public static void main(String[] args) {
        for (boolean asynchronous : new boolean[]{false, true}) {
            FormAggregationBenchmark fixture = new FormAggregationBenchmark();
            fixture.implementation = "native";
            fixture.fields = 16;
            fixture.delayed = asynchronous;
            fixture.setup();
            try {
                fixture.aggregate();
            } finally {
                fixture.close();
            }
        }
        System.out.println("Native/Reactor parity passed: empty, single, repeated, 16 fields; immediate and delayed");
    }

    @Internal
    private static final class ReactorFormBinder extends ServerBodyAnnotationBinder<Map> {
        ReactorFormBinder(MessageBodyHandlerRegistry registry, BeanProvider<FormFactory> factory) {
            super(ConversionService.SHARED, registry, factory);
        }

        @Override
        ExecutionFlow<Optional<Map>> transformForm(HttpRequest<?> request, ServerHttpRequest<?> server,
                                                  FormCapableHttpRequest<?> formRequest, ArgumentConversionContext<Map> context,
                                                  AvailableByteBody imm) {
            Map<@Nullable String, Object> values = new LinkedHashMap<>();
            PropagatedContext propagated = PropagatedContext.getOrEmpty();
            Mono<Optional<Map>> collected = Flux.from(formRequest.getRawFormFields(imm))
                // No prefetch: next field requested only when the current field completes.
                .concatMap(field -> ReactiveExecutionFlow.toPublisher(InternalByteBody.bufferFlow(field.byteBody())
                    .<Void>map(bytes -> {
                        try (bytes) {
                            addValue(values, field.metadata().name(), bytes.toString(formRequest.getCharacterEncoding()));
                        }
                        return null;
                    })))
                .doOnDiscard(RawFormField.class, RawFormField::close)
                .onErrorMap(error -> new IllegalStateException("Failed to load form fields", error))
                .then(Mono.fromSupplier(() -> ServerHttpRequestContext.withRequest(propagated, request).propagate(() -> {
                    Optional<Map> converted = conversionService.convert(values, context);
                    cacheDecodedBody(request, server, converted.orElse(null));
                    return converted;
                })));
            return ReactiveExecutionFlow.fromPublisher(collected);
        }

        @SuppressWarnings("unchecked")
        private static void addValue(Map<@Nullable String, Object> values, @Nullable String name, String text) {
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
    }

    @Internal
    private final class FormRequest extends HttpRequestWrapper<Object> implements FormCapableHttpRequest<Object>, AutoCloseable {
        private final List<RawFormField> parts = new ArrayList<>();

        FormRequest(int count, boolean repeated) {
            super(HttpRequest.POST("/form", null).contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
            for (int i = 0; i < count; i++) {
                parts.add(new RawFormField(new FormFieldMetadata(repeated ? "field" : "field" + i, null, MediaType.TEXT_PLAIN_TYPE),
                    BODIES.copyOf("value", StandardCharsets.UTF_8)));
            }
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return Flux.fromIterable(parts);
        }

        @Override
        public Publisher<RawFormField> getRawFormFields(ByteBody body) {
            ((CloseableByteBody) body).close();
            Flux<RawFormField> source = Flux.fromIterable(parts);
            return delayed ? source.subscribeOn(scheduler) : source;
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public ByteBody byteBody() {
            throw new UnsupportedOperationException("Only buffered transformation is measured");
        }

        @Override
        public void addDisposalResource(Runnable resource) {
            throw new UnsupportedOperationException("Only buffered transformation is measured");
        }

        @Override
        public void close() {
            parts.forEach(RawFormField::close);
        }
    }
}
