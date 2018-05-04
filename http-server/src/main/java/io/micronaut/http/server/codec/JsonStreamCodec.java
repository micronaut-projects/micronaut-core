/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import javax.inject.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Allow encoding objects using {@link MediaType#APPLICATION_JSON_STREAM}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonStreamCodec implements MediaTypeCodec {

    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);
    private final Provider<MediaTypeCodecRegistry> codecRegistryProvider;
    private final ByteBufferFactory byteBufferFactory;
    private MediaTypeCodecRegistry codecRegistry;

    /**
     * @param byteBufferFactory     A byte buffer factory
     * @param codecRegistryProvider A media type codec registry
     */
    public JsonStreamCodec(
        ByteBufferFactory byteBufferFactory,
        Provider<MediaTypeCodecRegistry> codecRegistryProvider) {
        this.byteBufferFactory = byteBufferFactory;
        this.codecRegistryProvider = codecRegistryProvider;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_JSON_STREAM_TYPE;
    }

    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        throw new CodecException("Decoding from input stream not supported in Server codec");
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            outputStream.write(encode(object));
        } catch (IOException e) {
            throw new CodecException("Error encoding object to stream: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        return encode(object, byteBufferFactory).toByteArray();
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        MediaTypeCodec jsonCodec = resolveMediaTypeCodecRegistry().findCodec(MediaType.APPLICATION_JSON_TYPE)
            .orElseThrow(() -> new CodecException("No possible JSON encoders found!"));
        ByteBuffer encoded = jsonCodec.encode(object, allocator);
        encoded.write(NEWLINE);
        return encoded;
    }

    private MediaTypeCodecRegistry resolveMediaTypeCodecRegistry() {
        if (this.codecRegistry == null) {
            this.codecRegistry = codecRegistryProvider.get();
        }
        return this.codecRegistry;
    }
}
