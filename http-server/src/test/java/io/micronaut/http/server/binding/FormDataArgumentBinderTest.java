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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.multipart.ReleasingFieldPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collection of a whole form, over a request whose raw form fields the test controls.
 */
class FormDataArgumentBinderTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final UploadContext CONTEXT = new UploadContext(Runnable::run, BODY_FACTORY, StandardCharsets.UTF_8, 1024, Long.MAX_VALUE);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fieldsQueuedBehindTheOneBeingReadAreClosedWhenTheCollectionIsCancelled(boolean delegating) {
        Fields fields = new Fields(delegating);
        FormDataArgumentBinder.Collection collection = FormDataArgumentBinder.start(CONTEXT, null, ConversionService.SHARED, fields.request());
        fields.emit("first");
        Upstream queued = fields.emit("second");
        // the handler completed without waiting for the form
        collection.cancel();
        assertTrue(queued.discarded, "the field that was not read yet is closed");
        fields.releaseAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fieldsQueuedBehindTheOneBeingReadAreClosedWhenTheBodyFails(boolean delegating) {
        Fields fields = new Fields(delegating);
        FormDataArgumentBinder.Collection collection = FormDataArgumentBinder.start(CONTEXT, null, ConversionService.SHARED, fields.request());
        fields.emit("first");
        Upstream queued = fields.emit("second");
        // the client reset the stream of the request: the field being read fails, and so do the
        // fields
        IOException reset = new IOException("reset");
        fields.bodies.getFirst().sharedBuffer().error(reset);
        fields.sink.tryEmitError(reset);
        assertTrue(collection.result().isCompletedExceptionally());
        assertTrue(queued.discarded, "the field that was not read yet is closed");
        fields.releaseAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fieldsQueuedBehindTheOneBeingReadAreClosedWhenTheRequestEnds(boolean delegating) {
        Fields fields = new Fields(delegating);
        FormDataArgumentBinder.start(CONTEXT, null, ConversionService.SHARED, fields.request());
        fields.emit("first");
        Upstream queued = fields.emit("second");
        fields.disposal.forEach(Runnable::run);
        assertTrue(queued.discarded, "the field that was not read yet is closed");
        fields.releaseAll();
    }

    @Test
    void fieldsOfAPublisherThatReleasesThemAreReadWithoutReactor() {
        AtomicReference<Subscriber<?>> subscriber = new AtomicReference<>();
        ReleasingFieldPublisher<RawFormField> source = subscriber::set;
        FormCapableHttpRequest<?> request = (FormCapableHttpRequest<?>) Proxy.newProxyInstance(FormDataArgumentBinderTest.class.getClassLoader(), new Class<?>[]{FormCapableHttpRequest.class}, (proxy, method, args) -> {
            if (method.getName().equals("getRawFormFields")) {
                return source;
            }
            if (method.getName().equals("addDisposalResource")) {
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        FormDataArgumentBinder.start(CONTEXT, null, ConversionService.SHARED, request);
        assertInstanceOf(FormFieldFlows.Concat.class, subscriber.get(), "subscribed without a Reactor operator");
    }

    /**
     * Text fields whose content never ends, so that the first one is being read while the
     * others wait. The fields come from a Reactor publisher, or from a publisher that delegates
     * to one, which releases them with the discard hook of the subscriber too.
     */
    private static final class Fields {
        final Sinks.Many<RawFormField> sink = Sinks.many().unicast().onBackpressureBuffer();
        final boolean delegating;
        final List<Runnable> disposal = new ArrayList<>();
        final List<ByteBodyFactory.StreamingBody> bodies = new ArrayList<>();

        Fields(boolean delegating) {
            this.delegating = delegating;
        }

        FormCapableHttpRequest<?> request() {
            Publisher<RawFormField> fields = delegating ? s -> sink.asFlux().subscribe(s) : sink.asFlux();
            return (FormCapableHttpRequest<?>) Proxy.newProxyInstance(FormDataArgumentBinderTest.class.getClassLoader(), new Class<?>[]{FormCapableHttpRequest.class}, (proxy, method, args) -> {
                if (method.getName().equals("getRawFormFields")) {
                    return fields;
                }
                if (method.getName().equals("addDisposalResource")) {
                    disposal.add((Runnable) args[0]);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        Upstream emit(String name) {
            Upstream upstream = new Upstream();
            ByteBodyFactory.StreamingBody body = BODY_FACTORY.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
            bodies.add(body);
            sink.tryEmitNext(new RawFormField(new FormFieldMetadata(name, null, null), body.rootBody()));
            return upstream;
        }

        void releaseAll() {
            for (ByteBodyFactory.StreamingBody body : bodies) {
                body.sharedBuffer().complete();
            }
        }
    }

    private static final class Upstream implements BufferConsumer.Upstream {
        boolean discarded;

        @Override
        public void onBytesConsumed(long bytesConsumed) {
            // the test does not check the consumed bytes
        }

        @Override
        public void allowDiscard() {
            discarded = true;
        }
    }
}
