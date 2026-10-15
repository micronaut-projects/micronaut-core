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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** A standalone probe avoids test-engine discovery loading Reactor-specific test signatures. */
@Internal
public final class ReactorAbsentBinderProbe {
    private ReactorAbsentBinderProbe() {
    }

    /**
     * Validate future body binding with Reactor removed from the runtime classpath.
     *
     * @param args Unused
     * @throws Exception If the native path cannot be initialized or executed
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("This probe takes no arguments");
        }
        if (ReactorAbsentBinderProbe.class.getClassLoader().getResource("reactor/core/publisher/Mono.class") != null) {
            throw new AssertionError("Reactor must be absent for this probe");
        }
        try (ApplicationContext context = ApplicationContext.run(); Request request = new Request()) {
            var binder = new CompletableFutureBodyBinder(context.getBean(ServerBodyAnnotationBinder.class));
            Argument argument = Argument.of(CompletableFuture.class, "body", Argument.of(Map.class));
            CompletableFuture<?> future = (CompletableFuture<?>) binder.bind(ConversionContext.of(argument), request).getValue().orElseThrow();
            if (!Map.of("a", 1).equals(future.get(10, TimeUnit.SECONDS))) {
                throw new AssertionError("Incorrect decoded body");
            }
        }
        try (ApplicationContext context = ApplicationContext.run(); FormRequest request = new FormRequest()) {
            var binder = new CompletableFutureBodyBinder(context.getBean(ServerBodyAnnotationBinder.class));
            Argument argument = Argument.of(CompletableFuture.class, "body", Argument.of(Map.class));
            CompletableFuture<?> future = (CompletableFuture<?>) binder.bind(ConversionContext.of(argument), request).getValue().orElseThrow();
            if (!Map.of("message", "hello").equals(future.get(10, TimeUnit.SECONDS))) {
                throw new AssertionError("Incorrect decoded form");
            }
        }
    }

    @Internal
    private static class Request extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody bytes = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
            .copyOf("{\"a\":1}", StandardCharsets.UTF_8);

        Request() {
            super(HttpRequest.POST("/body", null).contentType(MediaType.APPLICATION_JSON_TYPE));
        }

        @Override
        public ByteBody byteBody() {
            return bytes;
        }

        @Override
        public void close() {
            bytes.close();
        }
    }

    @Internal
    private static final class FormRequest extends Request implements FormCapableHttpRequest<Object> {
        FormRequest() {
            ((MutableHttpRequest<?>) getDelegate()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return subscriber -> subscriber.onSubscribe(new Subscription() {
                private boolean finished;

                @Override
                public void request(long n) {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    subscriber.onNext(new RawFormField(new FormFieldMetadata("message", null, MediaType.TEXT_PLAIN_TYPE),
                        ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).copyOf("hello", StandardCharsets.UTF_8)));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    finished = true;
                }
            });
        }

        @Override
        public Publisher<RawFormField> getRawFormFields(ByteBody bytes) {
            ((CloseableByteBody) bytes).close();
            return getRawFormFields();
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public void addDisposalResource(Runnable resource) {
            throw new UnsupportedOperationException("Probe does not register disposal resources");
        }
    }
}
