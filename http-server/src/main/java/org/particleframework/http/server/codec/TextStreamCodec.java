/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.codec;

import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.io.buffer.ByteBufferFactory;
import org.particleframework.core.type.Argument;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.CodecException;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.sse.Event;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A {@link MediaTypeCodec} that will encode {@link Event} objects in order to support Server Sent Events
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class TextStreamCodec implements MediaTypeCodec {
    private static final byte[] DATA_PREFIX = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_PREFIX = "event: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = "id: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RETRY_PREFIX = "retry: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMENT_PREFIX = ": ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);

    private final HttpServerConfiguration serverConfiguration;
    private final Provider<MediaTypeCodecRegistry> codecRegistryProvider;
    private final ByteBufferFactory byteBufferFactory;
    private MediaTypeCodecRegistry codecRegistry;

    public TextStreamCodec(
            HttpServerConfiguration serverConfiguration,
            ByteBufferFactory byteBufferFactory,
            Provider<MediaTypeCodecRegistry> codecRegistryProvider) {
        this.serverConfiguration = serverConfiguration;
        this.byteBufferFactory = byteBufferFactory;
        this.codecRegistryProvider = codecRegistryProvider;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.TEXT_EVENT_STREAM_TYPE;
    }

    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        throw new UnsupportedOperationException("This codec currently only supports encoding");
    }


    @Override
    public <T> T decode(Class<T> type, InputStream inputStream) throws CodecException {
        throw new UnsupportedOperationException("This codec currently only supports encoding");
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            outputStream.write( encode(object) );
        } catch (IOException e) {
            throw new CodecException("I/O error occurred encoding object to output stream: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        ByteBuffer buffer = encode(object, byteBufferFactory);
        return buffer.toByteArray();
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        Event<Object> event;
        if(object instanceof Event) {
            event = (Event<Object>) object;
        }
        else {
            event = Event.of(object);
        }
        Object data = event.getData();
        ByteBuffer body;
        if(data instanceof CharSequence) {
            body = allocator.copiedBuffer(data.toString().getBytes(serverConfiguration.getDefaultCharset()));
        }
        else {
            MediaTypeCodec jsonCodec = resolveMediaTypeCodecRegistry().findCodec(MediaType.APPLICATION_JSON_TYPE)
                    .orElseThrow(() -> new CodecException("No possible JSON encoders found!"));
            body = jsonCodec.encode(data, allocator);
        }
        ByteBuffer eventData = allocator.buffer(body.readableBytes() + 10);
        writeAttribute(eventData, COMMENT_PREFIX, event.getComment());
        writeAttribute(eventData, ID_PREFIX, event.getId());
        writeAttribute(eventData, EVENT_PREFIX, event.getName());
        Duration retry = event.getRetry();
        if(retry != null) {
            writeAttribute(eventData, RETRY_PREFIX, String.valueOf(retry.toMillis()));
        }
        // Write the data: prefix
        eventData.write(DATA_PREFIX)
                 .write(body)
                 .write(NEWLINE) // Write new lines for event separation
                 .write(NEWLINE);
        return eventData;
    }

    private MediaTypeCodecRegistry resolveMediaTypeCodecRegistry() {
        if(this.codecRegistry == null)
            this.codecRegistry = codecRegistryProvider.get();
        return this.codecRegistry;
    }

    protected void writeAttribute(ByteBuffer eventData, byte[] attribute, String value) {
        if(value != null) {
            eventData.write(attribute)
                    .write(value, serverConfiguration.getDefaultCharset())
                    .write(NEWLINE);
        }
    }
}
