/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.netty.body;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.ChunkedMessageBodyReader;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.TextPlainBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.body.JsonMessageHandler;
import io.micronaut.json.body.RawJsonStringHandler;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * A JSON body should not be escaped or parsed as a JSON value.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Singleton
@Replaces(RawJsonStringHandler.class)
@JsonMessageHandler.ProducesJson
@JsonMessageHandler.ConsumesJson
@Internal
public final class NettyRawJsonStringWriter implements MessageBodyWriter<CharSequence>, MessageBodyReader<String>, NettyBodyWriter<CharSequence>, ChunkedMessageBodyReader<String> {
    private final TextPlainBodyHandler defaultHandler = new TextPlainBodyHandler();

    @Override
    public void writeTo(HttpRequest<?> request, MutableHttpResponse<CharSequence> outgoingResponse, Argument<CharSequence> type, MediaType mediaType, CharSequence object, NettyWriteContext nettyContext) throws CodecException {
        NettyTextPlainHandler.writePlain(outgoingResponse, mediaType, object, nettyContext);
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        defaultHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return defaultHandler.read(type, mediaType, httpHeaders, inputStream);
    }

    @Override
    public Publisher<? extends String> readChunked(Argument<String> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
        return defaultHandler.readChunked(type, mediaType, httpHeaders, input);
    }
}
