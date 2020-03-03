/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.codec;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A {@link MediaTypeCodec} that will encode {@link Event} objects in order to support Server Sent Events.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Internal
@BootstrapContextCompatible
public class TextStreamCodec implements MediaTypeCodec {

    public static final String CONFIGURATION_QUALIFIER = "text-stream";

    private static final byte[] DATA_PREFIX = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_PREFIX = "event: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = "id: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RETRY_PREFIX = "retry: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMENT_PREFIX = ": ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);

    private final Provider<MediaTypeCodecRegistry> codecRegistryProvider;
    private final ByteBufferFactory byteBufferFactory;
    private final List<MediaType> additionalTypes;
    private final Charset defaultCharset;
    private MediaTypeCodecRegistry codecRegistry;

    /**
     * @param serverConfiguration   The HTTP server configuration
     * @param byteBufferFactory     A byte buffer factory
     * @param codecRegistryProvider A media type codec registry
     * @param codecConfiguration    The configuration for the codec
     */
    @Deprecated
    public TextStreamCodec(
        HttpServerConfiguration serverConfiguration,
        ByteBufferFactory byteBufferFactory,
        Provider<MediaTypeCodecRegistry> codecRegistryProvider,
        @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this(serverConfiguration.getDefaultCharset(), byteBufferFactory, codecRegistryProvider, codecConfiguration);
    }

    /**
     * @param applicationConfiguration The application configuration
     * @param byteBufferFactory     A byte buffer factory
     * @param codecRegistryProvider A media type codec registry
     * @param codecConfiguration    The configuration for the codec
     */
    @Inject
    public TextStreamCodec(
            ApplicationConfiguration applicationConfiguration,
            ByteBufferFactory byteBufferFactory,
            Provider<MediaTypeCodecRegistry> codecRegistryProvider,
            @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this(applicationConfiguration.getDefaultCharset(), byteBufferFactory, codecRegistryProvider, codecConfiguration);
    }

    /**
     * @param defaultCharset The default charset
     * @param byteBufferFactory     A byte buffer factory
     * @param codecRegistryProvider A media type codec registry
     * @param codecConfiguration    The configuration for the codec
     */
    protected TextStreamCodec(
            Charset defaultCharset,
            ByteBufferFactory byteBufferFactory,
            Provider<MediaTypeCodecRegistry> codecRegistryProvider,
            @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this.defaultCharset = defaultCharset;
        this.byteBufferFactory = byteBufferFactory;
        this.codecRegistryProvider = codecRegistryProvider;
        if (codecConfiguration != null) {
            this.additionalTypes = codecConfiguration.getAdditionalTypes();
        } else {
            this.additionalTypes = Collections.emptyList();
        }
    }

    @Override
    public Collection<MediaType> getMediaTypes() {
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(MediaType.TEXT_EVENT_STREAM_TYPE);
        mediaTypes.addAll(additionalTypes);
        return mediaTypes;
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
            outputStream.write(encode(object));
        } catch (IOException e) {
            throw new CodecException("I/O error occurred encoding object to output stream: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        ByteBuffer buffer = encode(object, byteBufferFactory);
        return buffer.toByteArray();
    }

    @SuppressWarnings("MagicNumber")
    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        Event<Object> event;
        if (object instanceof Event) {
            event = (Event<Object>) object;
        } else {
            event = Event.of(object);
        }
        Object data = event.getData();
        ByteBuffer body;
        if (data instanceof CharSequence) {
            body = allocator.copiedBuffer(data.toString().getBytes(defaultCharset));
        } else {
            MediaTypeCodec jsonCodec = resolveMediaTypeCodecRegistry().findCodec(MediaType.APPLICATION_JSON_TYPE)
                .orElseThrow(() -> new CodecException("No possible JSON encoders found!"));
            body = jsonCodec.encode(data, allocator);
        }
        ByteBuffer eventData = allocator.buffer(body.readableBytes() + 10);
        writeAttribute(eventData, COMMENT_PREFIX, event.getComment());
        writeAttribute(eventData, ID_PREFIX, event.getId());
        writeAttribute(eventData, EVENT_PREFIX, event.getName());
        Duration retry = event.getRetry();
        if (retry != null) {
            writeAttribute(eventData, RETRY_PREFIX, String.valueOf(retry.toMillis()));
        }

        // Write the data
        int idx = body.indexOf((byte) '\n');
        while (idx > -1) {
            int length = idx + 1;
            byte[] line = new byte[length];
            body.read(line, 0, length);
            eventData.write(DATA_PREFIX).write(line);
            idx = body.indexOf((byte) '\n');
        }
        if (body.readableBytes() > 0) {
            int length = body.readableBytes();
            byte[] line = new byte[length];
            body.read(line, 0, length);
            eventData.write(DATA_PREFIX).write(line);
        }

        // Write new lines for event separation
        eventData.write(NEWLINE).write(NEWLINE);
        return eventData;
    }

    private MediaTypeCodecRegistry resolveMediaTypeCodecRegistry() {
        if (this.codecRegistry == null) {
            this.codecRegistry = codecRegistryProvider.get();
        }
        return this.codecRegistry;
    }

    /**
     * @param eventData The byte buffer
     * @param attribute The attribute
     * @param value     The value
     */
    protected void writeAttribute(ByteBuffer eventData, byte[] attribute, String value) {
        if (value != null) {
            eventData.write(attribute)
                .write(value, defaultCharset)
                .write(NEWLINE);
        }
    }
}
