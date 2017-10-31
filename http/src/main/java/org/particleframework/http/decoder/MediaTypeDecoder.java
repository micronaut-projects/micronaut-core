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
package org.particleframework.http.decoder;

import org.particleframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Represents a decoder for a particular media type. For example JSON.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MediaTypeDecoder {

    /**
     * @return The media type of the decoder
     */
    MediaType getMediaType();

    /**
     * Decode the given type from the given {@link InputStream}
     *
     * @param type The type
     * @param inputStream The input stream
     * @param <T> The generic type
     * @return The decoded result
     * @throws DecodingException When the result cannot be decoded
     */
    <T> T decode(Class<T> type, InputStream inputStream) throws DecodingException;

    /**
     * Decode the given type from the given bytes
     *
     * @param type The type
     * @param bytes The bytes
     * @param <T> The decoded type
     * @return The decoded result
     *
     * @throws DecodingException When the result cannot be decoded
     */
    default <T> T decode(Class<T> type, byte[] bytes) throws DecodingException {
        return decode(type, new ByteArrayInputStream(bytes));
    }
}
