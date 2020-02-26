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
package io.micronaut.http.codec;

import io.micronaut.http.MediaType;

import java.util.Collection;
import java.util.Optional;

/**
 * <p>A registry of decoders.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MediaTypeCodecRegistry {

    /**
     * Find a codec for the given media type.
     *
     * @param mediaType The {@link MediaType}
     * @return The codec
     */
    Optional<MediaTypeCodec> findCodec(MediaType mediaType);

    /**
     * Find a codec for the given media type and target type.
     *
     * @param mediaType The {@link MediaType}
     * @param type      The type
     * @return The codec
     */
    Optional<MediaTypeCodec> findCodec(MediaType mediaType, Class<?> type);

    /**
     * @return The available codecs
     */
    Collection<MediaTypeCodec> getCodecs();

    /**
     * Create a new registry from the given codecs.
     *
     * @param codecs The decoders
     * @return The registry
     */
    static MediaTypeCodecRegistry of(MediaTypeCodec... codecs) {
        return new DefaultMediaTypeCodecRegistry(codecs);
    }

    /**
     * Create a new registry from the given codecs.
     *
     * @param codecs The decoders
     * @return The registry
     */
    static MediaTypeCodecRegistry of(Collection<MediaTypeCodec> codecs) {
        return new DefaultMediaTypeCodecRegistry(codecs);
    }
}
