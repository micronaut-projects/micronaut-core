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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of a copy of the body on a server whose request cannot decode the form fields of
 * other bytes than its own, the default of {@link FormCapableHttpRequest#getRawFormFields(ByteBody)}:
 * the split of the bytes the copy made for the fields is closed when decoding them is refused.
 */
class AsyncRequestBodyCopySplitReleaseTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void theSplitOfACopyIsClosedWhenTheRequestCannotDecodeItsFields() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            Upstream upstream = new Upstream();
            ByteBodyFactory.StreamingBody streaming = BODIES.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
            SimpleHttpRequest<Object> delegate = new SimpleHttpRequest<>(HttpMethod.POST, "/form", null);
            delegate.contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
            OtherFormRequest server = new OtherFormRequest(delegate, streaming.rootBody());
            DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, ctx.getBean(AsyncRequestBodyArgumentBinder.class));

            AsyncRequestBody copy = body.copy();
            FormParts parts = copy.parts();
            // the fields are decoded when the first part is asked for
            ExecutionException e = assertThrows(ExecutionException.class,
                () -> parts.part("name", part -> CompletableFuture.completedStage(null)).toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertInstanceOf(UnsupportedOperationException.class, e.getCause());

            body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
            server.disposal.forEach(Runnable::run);
            // the last reader of the bytes: they are discarded once no split is left open
            streaming.rootBody().close();
            assertTrue(upstream.discarded, "the split of the copy was closed");
            streaming.sharedBuffer().complete();
        }
    }

    @Test
    void theSplitOfACopyIsClosedWhenTheRequestCannotDecodeItsForm() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            Upstream upstream = new Upstream();
            ByteBodyFactory.StreamingBody streaming = BODIES.createStreamingBody(BodySizeLimits.UNLIMITED, upstream);
            SimpleHttpRequest<Object> delegate = new SimpleHttpRequest<>(HttpMethod.POST, "/form", null);
            delegate.contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
            OtherFormRequest server = new OtherFormRequest(delegate, streaming.rootBody());
            DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, ctx.getBean(AsyncRequestBodyArgumentBinder.class));

            // the form of a copy is read from a split of the bytes, never from the bytes of the request
            ExecutionException e = assertThrows(ExecutionException.class,
                () -> body.copy().form().toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertInstanceOf(UnsupportedOperationException.class, e.getCause());

            body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
            server.disposal.forEach(Runnable::run);
            // the last reader of the bytes: they are discarded once no split is left open
            streaming.rootBody().close();
            assertTrue(upstream.discarded, "the split of the copy was closed");
            streaming.sharedBuffer().complete();
        }
    }

    /**
     * A server request with a form body, which keeps the default
     * {@link FormCapableHttpRequest#getRawFormFields(ByteBody)}.
     */
    private static final class OtherFormRequest extends HttpRequestWrapper<Object> implements FormCapableHttpRequest<Object> {
        final List<Runnable> disposal = new ArrayList<>();
        private final ByteBody body;

        OtherFormRequest(HttpRequest<Object> delegate, ByteBody body) {
            super(delegate);
            this.body = body;
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
        public Publisher<RawFormField> getRawFormFields() {
            throw new AssertionError("A copy does not decode the bytes of the request");
        }

        @Override
        public boolean hasFormBody() {
            return true;
        }

        @Override
        public void addDisposalResource(Runnable dispose) {
            disposal.add(dispose);
        }
    }

    private static final class Upstream implements BufferConsumer.Upstream {
        volatile boolean discarded;

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
