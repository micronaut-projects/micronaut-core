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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.server.binding.DefaultServerRequestBinderRegistry;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The legacy Netty registry bean and the lifecycle use the same mutable shared registry. */
class SharedBinderRegistryTest {
    @Test
    void nettyAdapterRetainsTheSharedFormCapabilityForOtherTransports() {
        try (ApplicationContext context = ApplicationContext.run(); FormRequest request = new FormRequest()) {
            var binder = context.getBean(NettyBodyAnnotationBinder.class);
            assertSame(request, binder.formRequest(request));
            assertSame(request, binder.formRequest(binder.bodyOf(new HttpRequestWrapper<>(request))));
        }
    }

    @Test
    void registeringThroughTheLegacyBeanAffectsTheLifecycle() {
        try (ApplicationContext context = ApplicationContext.run()) {
            RequestBinderRegistry shared = context.getBean(RequestBinderRegistry.class);
            assertInstanceOf(DefaultServerRequestBinderRegistry.class, shared);
            NettyServerRequestBinderRegistry legacy = context.getBean(NettyServerRequestBinderRegistry.class);
            TypedRequestArgumentBinder<Marker> binder = new TypedRequestArgumentBinder<>() {
                @Override
                public Argument<Marker> argumentType() {
                    return Argument.of(Marker.class);
                }

                @Override
                public BindingResult<Marker> bind(ArgumentConversionContext<Marker> conversion, HttpRequest<?> source) {
                    return () -> Optional.of(new Marker());
                }
            };
            legacy.addArgumentBinder(binder);
            assertSame(binder, shared.findArgumentBinder(Argument.of(Marker.class)).orElseThrow());
            assertSame(binder, context.getBean(RequestArgumentSatisfier.class).getBinderRegistry()
                .findArgumentBinder(Argument.of(Marker.class)).orElseThrow());
        }
    }

    private record Marker() {
    }

    @Internal
    private static final class FormRequest extends HttpRequestWrapper<Object> implements FormCapableHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).createEmpty();

        FormRequest() {
            super(HttpRequest.POST("/form", null).contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
        }

        @Override
        public Publisher<RawFormField> getRawFormFields() {
            return Flux.empty();
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
        public void addDisposalResource(Runnable resource) {
            throw new UnsupportedOperationException("The capability resolution test does not claim fields");
        }

        @Override
        public void close() {
            body.close();
        }
    }
}
