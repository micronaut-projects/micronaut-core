/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.runtime.http.codec;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A codec that handles {@link MediaType#TEXT_PLAIN}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@BootstrapContextCompatible
public class TextPlainCodec implements MediaTypeCodec {

    public static final String CONFIGURATION_QUALIFIER = "text";

    private final Charset defaultCharset;
    private final List<MediaType> additionalTypes;

    /**
     * @param defaultCharset      The default charset used for serialization and deserialization
     * @param codecConfiguration  The configuration for the codec
     */
    @Inject
    public TextPlainCodec(@Value("${" + ApplicationConfiguration.DEFAULT_CHARSET + "}") Optional<Charset> defaultCharset,
                          @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this.defaultCharset = defaultCharset.orElse(StandardCharsets.UTF_8);
        if (codecConfiguration != null) {
            this.additionalTypes = codecConfiguration.getAdditionalTypes();
        } else {
            this.additionalTypes = Collections.emptyList();
        }
    }

    /**
     * @param defaultCharset The default charset used for serialization and deserialization
     */
    public TextPlainCodec(Charset defaultCharset) {
        this.defaultCharset = defaultCharset != null ? defaultCharset : StandardCharsets.UTF_8;
        this.additionalTypes = Collections.emptyList();
    }

    @Override
    public Collection<MediaType> getMediaTypes() {
        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(MediaType.TEXT_PLAIN_TYPE);
        mediaTypes.addAll(additionalTypes);
        return mediaTypes;
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        String text = buffer.toString(defaultCharset);
        return ConversionService.SHARED
            .convert(text, type)
            .orElseThrow(() -> new CodecException("Cannot decode byte buffer with value [" + text + "] to type: " + type));
    }

    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        if (CharSequence.class.isAssignableFrom(type.getType())) {
            try {
                return (T) IOUtils.readText(new BufferedReader(new InputStreamReader(inputStream, defaultCharset)));
            } catch (IOException e) {
                throw new CodecException("Error decoding string from stream: " + e.getMessage());
            }
        }
        throw new UnsupportedOperationException("codec only supports decoding objects to string");
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        byte[] bytes = encode(object);
        try {
            outputStream.write(bytes);
        } catch (IOException e) {
            throw new CodecException("Error writing encoding bytes to stream: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        return object.toString().getBytes(defaultCharset);
    }

    @Override
    public <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
        byte[] bytes = encode(object);

        return allocator.wrap(bytes);
    }
}
