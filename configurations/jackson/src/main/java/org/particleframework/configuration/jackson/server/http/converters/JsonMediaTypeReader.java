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
package org.particleframework.configuration.jackson.server.http.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import org.particleframework.http.MediaType;
import org.particleframework.http.server.netty.converters.MediaTypeReader;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * A {@link MediaTypeReader} for JSON
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonMediaTypeReader<T> implements MediaTypeReader<T> {

    private final ObjectMapper objectMapper;

    JsonMediaTypeReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_JSON_TYPE;
    }

    @Override
    public T read(Class<T> type, ByteBuf byteBuf, Charset charset) throws IOException {
        InputStreamReader reader = new InputStreamReader(new ByteBufInputStream(byteBuf), charset);
        return objectMapper.readValue(reader, type);
    }
}
