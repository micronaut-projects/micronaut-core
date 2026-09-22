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
package io.micronaut.http;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.cookie.Cookie;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutableByteBodyHttpResponseTest {

    static CloseableByteBody bytes(String s) {
        return AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A byte body response that counts its closes.
     */
    static final class TrackingResponse<B> extends HttpResponseWrapper<B> implements ByteBodyHttpResponse<B> {
        final CloseableByteBody body;
        int closed;

        TrackingResponse(HttpResponse<B> delegate, CloseableByteBody body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public void close() {
            closed++;
            body.close();
        }
    }

    static TrackingResponse<Object> tracking() {
        MutableHttpResponse<Object> response = HttpResponse.status(HttpStatus.CREATED, "Made");
        response.header("X-A", "1").header("X-A", "2");
        response.setAttribute("attr", "value");
        return new TrackingResponse<>(response, bytes("hello"));
    }

    @Test
    void copiesStatusHeadersAndAttributes() {
        TrackingResponse<Object> original = tracking();
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(original);
        assertEquals(201, mutable.code());
        assertEquals("Made", mutable.reason());
        assertEquals(java.util.List.of("1", "2"), mutable.getHeaders().getAll("X-A"));
        assertEquals("value", mutable.getAttribute("attr").orElseThrow());
        assertSame(original.body, mutable.byteBody());
        assertTrue(mutable.hasByteBody());
        assertTrue(mutable.getBody().isEmpty());
        assertEquals("201 Made", mutable.toString());
        assertSame(mutable, mutable.toMutableResponse());
        assertSame(mutable, MutableByteBodyHttpResponse.of(mutable));

        // changes do not touch the original
        mutable.status(404, "Gone");
        mutable.getHeaders().add("X-B", "b");
        mutable.getAttributes().put("attr2", "v2");
        assertEquals(404, mutable.code());
        assertEquals("Gone", mutable.reason());
        assertEquals(201, original.code());
        assertFalse(original.getHeaders().contains("X-B"));
        assertEquals(0, original.closed);
    }

    @Test
    void cookiesAndBodyWriter() {
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(tracking());
        mutable.cookie(Cookie.of("c", "v"));
        assertEquals("v", mutable.getCookie("c").orElseThrow().getValue());
        assertEquals(1, mutable.getCookies().getAll().size());

        @SuppressWarnings("unchecked")
        MutableByteBodyHttpResponse<Object> typed = (MutableByteBodyHttpResponse<Object>) mutable;
        MessageBodyWriter<Object> writer = new MessageBodyWriter<>() {
            @Override
            public void writeTo(Argument<Object> type, MediaType mediaType, Object object, io.micronaut.core.type.MutableHeaders outgoingHeaders, OutputStream outputStream) {
            }
        };
        assertSame(typed, typed.bodyWriter(writer));
        assertSame(writer, typed.getBodyWriter().orElseThrow());
    }

    @Test
    void objectBodyReplacesAndClosesBytes() {
        TrackingResponse<Object> original = tracking();
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(original);

        // null body with bytes present has no effect
        mutable.body(null);
        assertTrue(mutable.hasByteBody());
        assertEquals(0, original.closed);

        MutableHttpResponse<String> replaced = mutable.body("object");
        assertSame(mutable, replaced);
        assertFalse(mutable.hasByteBody());
        assertEquals("object", mutable.getBody().orElseThrow());
        assertEquals(1, original.closed);

        // another body replaces the object body, the bytes stay closed once
        mutable.body("second");
        assertEquals("second", mutable.getBody().orElseThrow());
        mutable.body(null);
        assertTrue(mutable.getBody().isEmpty());
        assertFalse(mutable.hasByteBody());
        assertEquals(1, original.closed);

        ((ByteBodyHttpResponse<?>) mutable).close();
        assertEquals(1, original.closed);
    }

    @Test
    void closeIsIdempotent() {
        TrackingResponse<Object> original = tracking();
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(original);
        ((ByteBodyHttpResponse<?>) mutable).close();
        ((ByteBodyHttpResponse<?>) mutable).close();
        assertEquals(1, original.closed);
    }

    @Test
    void ofResponseAndBytes() {
        MutableHttpResponse<Object> response = HttpResponse.status(HttpStatus.ACCEPTED);
        response.header("X-C", "c");
        CloseableByteBody body = bytes("abc");
        MutableByteBodyHttpResponse<?> mutable = MutableByteBodyHttpResponse.of(response, body);
        assertEquals(202, mutable.code());
        assertEquals("c", mutable.getHeaders().get("X-C"));
        assertSame(body, mutable.byteBody());
        ((ByteBodyHttpResponse<?>) mutable).close();
    }

    @Test
    void byteBodyResponseDefaults() {
        ByteBodyHttpResponse<?> wrapped = ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok(), bytes("x"));
        assertTrue(wrapped.hasByteBody());
        MutableHttpResponse<?> mutable = wrapped.toMutableResponse();
        assertInstanceOf(MutableByteBodyHttpResponse.class, mutable);

        ByteBodyHttpResponse<?> withBody = ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok("obj"), bytes("x"));
        assertFalse(withBody.hasByteBody());
        withBody.close();
        ((ByteBodyHttpResponse<?>) mutable).close();
    }

    @Test
    void wrapperToMutableResponse() {
        // mutable wrapper returns itself
        MutableHttpResponse<Object> ok = HttpResponse.ok();
        HttpResponseWrapper<Object> mutableWrapper = new MutableWrapper<>(ok);
        assertSame(mutableWrapper, mutableWrapper.toMutableResponse());

        // a wrapper that is a byte body response
        TrackingResponse<Object> direct = tracking();
        MutableHttpResponse<?> m1 = direct.toMutableResponse();
        assertInstanceOf(MutableByteBodyHttpResponse.class, m1);
        assertSame(direct.body, ((ByteBodyHttpResponse<?>) m1).byteBody());

        // a plain wrapper around a byte body response: status/headers of the wrapper, moved bytes
        TrackingResponse<Object> inner = tracking();
        HttpResponseWrapper<Object> outer = new HttpResponseWrapper<>(new HttpResponseWrapper<>(inner)) {
            @Override
            public HttpStatus getStatus() {
                return HttpStatus.I_AM_A_TEAPOT;
            }

            @Override
            public int code() {
                return 418;
            }
        };
        assertSame(inner, HttpResponseWrapper.wrappedByteBodyResponse(outer));
        MutableHttpResponse<?> m2 = outer.toMutableResponse();
        assertInstanceOf(MutableByteBodyHttpResponse.class, m2);
        assertEquals(418, m2.code());
        assertNotSame(inner.body, ((ByteBodyHttpResponse<?>) m2).byteBody());
        ((ByteBodyHttpResponse<?>) m2).close();

        // a wrapper with an object body closes the wrapped bytes
        TrackingResponse<Object> inner2 = tracking();
        HttpResponseWrapper<Object> withBody = new HttpResponseWrapper<>(inner2) {
            @Override
            public java.util.Optional<Object> getBody() {
                return java.util.Optional.of("object");
            }
        };
        MutableHttpResponse<?> m3 = withBody.toMutableResponse();
        assertFalse(m3 instanceof ByteBodyHttpResponse<?>);
        assertEquals(1, inner2.closed);
        assertEquals("object", m3.getBody().orElseThrow());

        // wrapped bytes already replaced (and then the object body cleared): plain mutable copy
        TrackingResponse<Object> tracked3 = tracking();
        MutableByteBodyHttpResponse<?> inner3 = MutableByteBodyHttpResponse.of(tracked3);
        inner3.body("replaced");
        inner3.body(null);
        MutableHttpResponse<?> m4 = new HttpResponseWrapper<>(inner3).toMutableResponse();
        assertFalse(m4 instanceof ByteBodyHttpResponse<?>);
        assertEquals(201, m4.code());
        assertEquals(1, tracked3.closed);

        // a plain wrapper of a plain response
        HttpResponseWrapper<Object> plain = new HttpResponseWrapper<>(HttpResponse.ok());
        assertSame(null, HttpResponseWrapper.wrappedByteBodyResponse(plain));
        MutableHttpResponse<?> m5 = plain.toMutableResponse();
        assertEquals(200, m5.code());
        assertFalse(m5 instanceof ByteBodyHttpResponse<?>);
    }

    static final class MutableWrapper<B> extends HttpResponseWrapper<B> implements MutableHttpResponse<B> {
        MutableWrapper(MutableHttpResponse<B> delegate) {
            super(delegate);
        }

        private MutableHttpResponse<B> mutable() {
            return (MutableHttpResponse<B>) getDelegate();
        }

        @Override
        public MutableHttpResponse<B> cookie(Cookie cookie) {
            mutable().cookie(cookie);
            return this;
        }

        @Override
        public MutableHttpHeaders getHeaders() {
            return mutable().getHeaders();
        }

        @Override
        public io.micronaut.core.convert.value.MutableConvertibleValues<Object> getAttributes() {
            return mutable().getAttributes();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> MutableHttpResponse<T> body(T body) {
            mutable().body(body);
            return (MutableHttpResponse<T>) this;
        }

        @Override
        public MutableHttpResponse<B> status(int status, CharSequence message) {
            mutable().status(status, message);
            return this;
        }
    }
}
