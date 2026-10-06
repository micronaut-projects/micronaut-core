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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collection of a whole form, over a request whose raw form fields the test controls.
 */
class FormDataArgumentBinderTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final UploadContext CONTEXT = new UploadContext(Runnable::run, BODY_FACTORY, StandardCharsets.UTF_8, 1024, Long.MAX_VALUE);

    @Test
    void fieldsQueuedBehindTheOneBeingReadAreClosedWhenTheCollectionIsCancelled() {
        Fields fields = new Fields();
        FormDataArgumentBinder.Collection collection = FormDataArgumentBinder.start(CONTEXT, null, ConversionService.SHARED, fields.request());
        fields.emit("first");
        Upstream queued = fields.emit("second");
        // the handler completed without waiting for the form
        collection.cancel();
        assertTrue(queued.discarded, "the field that was not read yet is closed");
        fields.releaseAll();
    }

    @Test
    void fieldsQueuedBehindTheOneBeingReadAreClosedWhenTheBodyFails() {
        Fields fields = new Fields();
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

    @Test
    void fieldsQueuedBehindTheOneBeingReadAreClosedWhenTheRequestEnds() {
        Fields fields = new Fields();
        FormDataArgumentBinder.start(CONTEXT, null, ConversionService.SHARED, fields.request());
        fields.emit("first");
        Upstream queued = fields.emit("second");
        fields.disposal.forEach(Runnable::run);
        assertTrue(queued.discarded, "the field that was not read yet is closed");
        fields.releaseAll();
    }

    /**
     * Text fields whose content never ends, so that the first one is being read while the
     * others wait.
     */
    private static final class Fields {
        final Sinks.Many<RawFormField> sink = Sinks.many().unicast().onBackpressureBuffer();
        final List<Runnable> disposal = new ArrayList<>();
        final List<ByteBodyFactory.StreamingBody> bodies = new ArrayList<>();

        FormCapableHttpRequest<?> request() {
            return (FormCapableHttpRequest<?>) Proxy.newProxyInstance(FormDataArgumentBinderTest.class.getClassLoader(), new Class<?>[]{FormCapableHttpRequest.class}, (proxy, method, args) -> {
                if (method.getName().equals("getRawFormFields")) {
                    return sink.asFlux();
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
