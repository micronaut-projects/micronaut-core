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

import org.particleframework.core.io.IOUtils;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.io.buffer.ByteBufferAllocator;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.CodecException;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.http.server.HttpServerConfiguration;

import javax.inject.Singleton;
import java.io.*;

/**
 * A codec that handles {@link MediaType#TEXT_PLAIN}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class TextPlainCodec implements MediaTypeCodec{

    private final HttpServerConfiguration serverConfiguration;

    public TextPlainCodec(HttpServerConfiguration serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.TEXT_PLAIN_TYPE;
    }

    @Override
    public <T> T decode(Class<T> type, InputStream inputStream) throws CodecException {
        if(CharSequence.class.isAssignableFrom(type)) {
            try {
                return (T) IOUtils.readText(new BufferedReader(new InputStreamReader(inputStream, serverConfiguration.getDefaultCharset())));
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
        return object.toString().getBytes(serverConfiguration.getDefaultCharset());
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferAllocator allocator) throws CodecException {
        String string = object.toString();
        int len = string.length();
        return allocator.buffer(len, len)
                        .write(string.getBytes(serverConfiguration.getDefaultCharset()));
    }
}
