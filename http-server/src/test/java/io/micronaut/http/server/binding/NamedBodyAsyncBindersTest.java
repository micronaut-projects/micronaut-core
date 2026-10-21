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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The named component of the body bound for a {@code @Body("name")} future or publisher argument,
 * calling the binders directly with a request that is not a Netty request.
 */
class NamedBodyAsyncBindersTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void aNamedFutureBindsTheComponentOfAJsonBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"name\":\"Fred\",\"age\":10}")) {
            var future = (CompletableFuture<?>) bind(ctx, CompletableFutureBodyBinder.class,
                Argument.of(CompletableFuture.class, "arg", named("name"), Argument.STRING), server);
            assertEquals("Fred", future.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void aNamedFutureBindsTheFieldOfAFormBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             FormRequest server = new FormRequest("message", "hello")) {
            var future = (CompletableFuture<?>) bind(ctx, CompletableFutureBodyBinder.class,
                Argument.of(CompletableFuture.class, "arg", named("message"), Argument.STRING), server);
            assertEquals("hello", future.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void aNamedFutureOfAMissingComponentIsNotBound() {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"name\":\"Fred\"}")) {
            var result = result(ctx, CompletableFutureBodyBinder.class,
                Argument.of(CompletableFuture.class, "arg", named("missing"), Argument.STRING), server);
            assertFalse(result.getValue().isPresent());
        }
    }

    @Test
    void aNamedPublisherOfAMissingComponentIsNotBound() {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"name\":\"Fred\"}")) {
            var result = result(ctx, PublisherBodyBinder.class,
                Argument.of(Mono.class, "arg", named("missing"), Argument.STRING), server);
            assertFalse(result.getValue().isPresent());
        }
    }

    @Test
    void aNamedMonoBindsTheComponent() {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"name\":\"Fred\"}")) {
            var mono = (Mono<?>) bind(ctx, PublisherBodyBinder.class,
                Argument.of(Mono.class, "arg", named("name"), Argument.STRING), server);
            assertEquals("Fred", mono.block(TIMEOUT));
        }
    }

    @Test
    void aNamedFluxEmitsTheElementsOfTheComponent() {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"names\":[\"a\",\"b\",\"c\"],\"other\":1}")) {
            var publisher = (Publisher<?>) bind(ctx, PublisherBodyBinder.class,
                Argument.of(Flux.class, "arg", named("names"), Argument.STRING), server);
            assertEquals(List.of("a", "b", "c"), Flux.from(publisher).collectList().block(TIMEOUT));
        }
    }

    @Test
    void anUnnamedFutureStillBindsTheWholeBody() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"name\":\"Fred\"}")) {
            var future = (CompletableFuture<?>) bind(ctx, CompletableFutureBodyBinder.class,
                Argument.of(CompletableFuture.class, "arg", unnamed(), Argument.of(Map.class)), server);
            assertEquals(Map.of("name", "Fred"), future.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void anUnnamedMonoStillBindsTheWholeBody() {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("{\"name\":\"Fred\"}")) {
            var mono = (Mono<?>) bind(ctx, PublisherBodyBinder.class,
                Argument.of(Mono.class, "arg", unnamed(), Argument.of(Map.class)), server);
            assertEquals(Map.of("name", "Fred"), mono.block(TIMEOUT));
        }
    }

    @Test
    void anUnnamedFluxStillEmitsTheElementsOfTheWholeBody() {
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonRequest server = new JsonRequest("[{\"a\":1},{\"a\":2}]")) {
            var publisher = (Publisher<?>) bind(ctx, PublisherBodyBinder.class,
                Argument.of(Flux.class, "arg", unnamed(), Argument.of(Map.class)), server);
            assertEquals(List.of(Map.of("a", 1), Map.of("a", 2)), Flux.from(publisher).collectList().block(TIMEOUT));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <B extends ArgumentBinder<?, HttpRequest<?>>> ArgumentBinder<Object, HttpRequest<?>> binder(ApplicationContext ctx, Class<B> type) {
        ServerBodyAnnotationBinder<Object> body = ctx.getBean(ServerBodyAnnotationBinder.class);
        if (type == CompletableFutureBodyBinder.class) {
            return (ArgumentBinder) new CompletableFutureBodyBinder(body);
        }
        return (ArgumentBinder) new PublisherBodyBinder(body, ctx.getConversionService());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <B extends ArgumentBinder<?, HttpRequest<?>>> ArgumentBinder.BindingResult<Object> result(ApplicationContext ctx, Class<B> type, Argument argument, ServerHttpRequest<?> server) {
        return binder(ctx, type).bind(ConversionContext.of(argument), new HttpRequestWrapper<>(server));
    }

    private static <B extends ArgumentBinder<?, HttpRequest<?>>> Object bind(ApplicationContext ctx, Class<B> type, Argument<?> argument, ServerHttpRequest<?> server) {
        return result(ctx, type, argument, server).getValue().orElseThrow();
    }

    private static AnnotationMetadata named(String name) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Body.class.getName(), Map.of(AnnotationMetadata.VALUE_MEMBER, name));
        return metadata;
    }

    private static AnnotationMetadata unnamed() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Body.class.getName(), Map.of());
        return metadata;
    }

    /**
     * A server request with a JSON body, like the request of a servlet server.
     */
    private static class JsonRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body;

        JsonRequest(String content) {
            this(content, MediaType.APPLICATION_JSON_TYPE);
        }

        JsonRequest(String content, MediaType contentType) {
            super(new SimpleHttpRequest<>(HttpMethod.POST, "/body", null).contentType(contentType));
            this.body = BODIES.copyOf(content, StandardCharsets.UTF_8);
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return BODIES;
        }

        @Override
        public void close() {
            body.close();
        }
    }

    /**
     * A server request with a form body of one field.
     */
    private static final class FormRequest extends JsonRequest implements FormCapableHttpRequest<Object> {
        private final RawFormField field;
        private final List<Runnable> disposal = new ArrayList<>();

        FormRequest(String name, String value) {
            super("form", MediaType.MULTIPART_FORM_DATA_TYPE);
            field = new RawFormField(new FormFieldMetadata(name, null, MediaType.TEXT_PLAIN_TYPE), BODIES.copyOf(value, StandardCharsets.UTF_8));
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return Flux.just(field);
        }

        @Override
        public Publisher<RawFormField> getRawFormFields(ByteBody bytes) {
            ((CloseableByteBody) bytes).close();
            return Flux.just(field);
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public void addDisposalResource(Runnable dispose) {
            disposal.add(dispose);
        }

        @Override
        public void close() {
            disposal.forEach(Runnable::run);
            field.close();
            super.close();
        }
    }
}
