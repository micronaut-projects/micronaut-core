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
package org.particleframework.runtime.http.codec;

import org.particleframework.context.annotation.Value;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.io.IOUtils;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.io.buffer.ByteBufferFactory;
import org.particleframework.core.type.Argument;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.CodecException;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.runtime.ApplicationConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A codec that handles {@link MediaType#TEXT_PLAIN}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class TextPlainCodec implements MediaTypeCodec {

    private final Charset defaultCharset;

    @Inject public TextPlainCodec(@Value(ApplicationConfiguration.DEFAULT_CHARSET) Optional<Charset> defaultCharset) {
        this.defaultCharset = defaultCharset.orElse(StandardCharsets.UTF_8);
    }

    public TextPlainCodec(Charset defaultCharset) {
        this.defaultCharset = defaultCharset != null ? defaultCharset : StandardCharsets.UTF_8;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.TEXT_PLAIN_TYPE;
    }

    @Override
    public <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
        String text = buffer.toString(defaultCharset);
        return ConversionService.SHARED.convert(
                text,
                type
        ).orElseThrow(()-> new CodecException("Cannot decode byte buffer with value ["+text+"] to type: " + type));
    }

    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        if(CharSequence.class.isAssignableFrom(type.getType())) {
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
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        String string = object.toString();
        int len = string.length();
        return allocator.buffer(len, len)
                        .write(string.getBytes(defaultCharset));
    }
}
