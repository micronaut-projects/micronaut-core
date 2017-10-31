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
package org.particleframework.configuration.jackson.server.http.decoders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.http.MediaType;
import org.particleframework.http.decoder.DecodingException;
import org.particleframework.http.decoder.MediaTypeDecoder;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link MediaTypeDecoder} for JSON and Jackson
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonMediaTypeDecoder implements MediaTypeDecoder {
    private final ObjectMapper objectMapper;

    public JsonMediaTypeDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_JSON_TYPE;
    }

    @Override
    public <T> T decode(Class<T> type, InputStream inputStream) throws DecodingException {
        try {
            return objectMapper.readValue(inputStream, type);
        } catch (IOException e) {
            throw new DecodingException("Error decoding JSON stream for type ["+type.getName()+"]: ");
        }
    }
}
