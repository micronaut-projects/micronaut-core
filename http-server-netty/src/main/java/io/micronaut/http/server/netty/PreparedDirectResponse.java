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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The response a direct route gives as a value, prepared once, when the routes are built, see
 * {@link NettyDirectRouteSupport#prepare(HttpResponse)}: its headers are validated once, and its
 * body is a buffer every request writes a duplicate of. The route answers every request with this
 * instance, which is never changed: {@link #getHeaders()}, {@link #getAttributes()} and
 * {@link #body()} are copies.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class PreparedDirectResponse implements HttpResponse<byte[]> {

    private final HttpResponseStatus status;
    private final io.netty.handler.codec.http.HttpHeaders headers;
    private final byte @Nullable [] body;
    private final Map<String, Object> attributes;
    /**
     * The buffer of the body, released when the server is shut down, see {@link #release()}.
     */
    private final ByteBuf buffer;
    /**
     * The buffer of the body, which releasing a duplicate of does not release.
     */
    private final ByteBuf content;

    private PreparedDirectResponse(HttpResponseStatus status,
                                   io.netty.handler.codec.http.HttpHeaders headers,
                                   byte @Nullable [] body,
                                   Map<String, Object> attributes) {
        this.status = status;
        this.headers = headers;
        this.body = body;
        this.attributes = attributes;
        this.buffer = body == null || body.length == 0
            ? Unpooled.EMPTY_BUFFER
            : Unpooled.directBuffer(body.length).writeBytes(body);
        this.content = Unpooled.unreleasableBuffer(buffer);
    }

    /**
     * @param response The response given as a value
     * @return The prepared response, or {@code null} if its body is neither empty nor bytes, e.g. a
     *         body a message body writer writes for each request
     */
    static @Nullable PreparedDirectResponse of(HttpResponse<?> response) {
        Object body = response.body();
        if (body != null && !(body instanceof byte[])) {
            return null;
        }
        // validated here, once, and encoded once: the encoder copies the bytes of an AsciiString
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        response.getHeaders().forEach((String name, List<String> values) -> {
            CharSequence encodedName = ascii(name);
            for (String value : values) {
                headers.add(encodedName, ascii(value));
            }
        });
        Map<String, Object> attributes = new LinkedHashMap<>();
        response.getAttributes().forEach((name, value) -> {
            if (value != null) {
                attributes.put(name, value);
            }
        });
        return new PreparedDirectResponse(HttpResponseStatus.valueOf(response.code(), response.reason()), headers,
            body == null ? null : ((byte[]) body).clone(), Map.copyOf(attributes));
    }

    /**
     * @param text A header name or value
     * @return The text as an {@link AsciiString} if it is ASCII, otherwise the text itself, which
     *         the encoder encodes like the header of any other response
     */
    private static CharSequence ascii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return text;
            }
        }
        return new AsciiString(text);
    }

    /**
     * @return The head of a response to a request: a copy of the headers, which the server
     *         completes
     */
    io.netty.handler.codec.http.HttpResponse head() {
        return new DefaultHttpResponse(HttpVersion.HTTP_1_1, status, headers.copy());
    }

    /**
     * @return The body of a response to a request: a duplicate of the shared buffer, which
     *         releasing does not release
     */
    ByteBuf content() {
        return content.duplicate();
    }

    /**
     * Release the buffer of the body, once no request is answered any more.
     */
    void release() {
        if (buffer.refCnt() > 0) {
            buffer.release();
        }
    }

    @Override
    public int code() {
        return status.code();
    }

    @Override
    public String reason() {
        return status.reasonPhrase();
    }

    @Override
    public HttpHeaders getHeaders() {
        return new NettyHttpHeaders(headers.copy(), ConversionService.SHARED);
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return MutableConvertibleValues.of(new LinkedHashMap<>(attributes));
    }

    /**
     * @return A copy of the body
     */
    @Override
    public Optional<byte[]> getBody() {
        return Optional.ofNullable(body());
    }

    /**
     * @return A copy of the body
     */
    @Override
    public byte @Nullable [] body() {
        return body == null ? null : body.clone();
    }
}
