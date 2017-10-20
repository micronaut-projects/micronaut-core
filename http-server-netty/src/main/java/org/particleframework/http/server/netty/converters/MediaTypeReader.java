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
package org.particleframework.http.server.netty.converters;

import io.netty.buffer.ByteBuf;
import org.particleframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * Represents a mapping between a Java type and a {@link MediaType}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MediaTypeReader<T> {
    /**
     * @return The media type
     */
    MediaType getMediaType();

    /**
     * @return The Java type
     */
    T read(Class<T> type, ByteBuf byteBuf, Charset charset) throws IOException;
}
