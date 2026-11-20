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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.netty.buffer.ByteBuf;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reader that always fails to decode, recording the buffers it was given.
 */
@Requires(property = "spec.name", value = "WebSocketReaderLeakSpec")
@Singleton
@Consumes(FailingMessageBodyReader.MEDIA_TYPE)
public class FailingMessageBodyReader implements MessageBodyReader<Message> {
    public static final String MEDIA_TYPE = "application/x-failing";

    final List<ByteBuf> buffers = new CopyOnWriteArrayList<>();

    @Override
    public Message read(Argument<Message> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
        buffers.add((ByteBuf) byteBuffer.asNativeBuffer());
        // the default implementation releases the buffer only on success
        return MessageBodyReader.super.read(type, mediaType, httpHeaders, byteBuffer);
    }

    @Override
    public Message read(Argument<Message> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        throw new CodecException("Malformed message");
    }
}
