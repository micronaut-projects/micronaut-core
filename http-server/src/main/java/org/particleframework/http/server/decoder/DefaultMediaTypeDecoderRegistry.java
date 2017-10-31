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
package org.particleframework.http.server.decoder;

import org.particleframework.http.MediaType;
import org.particleframework.http.decoder.MediaTypeDecoder;
import org.particleframework.http.decoder.MediaTypeDecoderRegistry;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link MediaTypeDecoder} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultMediaTypeDecoderRegistry implements MediaTypeDecoderRegistry{

    Map<String, MediaTypeDecoder> decodersByExtension = new LinkedHashMap<>(3);
    Map<MediaType, MediaTypeDecoder> decodersByType = new LinkedHashMap<>(3);

    DefaultMediaTypeDecoderRegistry(MediaTypeDecoder...decoders) {
        if(decoders != null) {
            for (MediaTypeDecoder decoder : decoders) {
                MediaType mediaType = decoder.getMediaType();
                if(mediaType != null) {
                    decodersByExtension.put(mediaType.getExtension(), decoder);
                    decodersByType.put(mediaType, decoder);
                }
            }
        }
    }

    @Override
    public Optional<MediaTypeDecoder> findDecoder(MediaType mediaType) {
        if(mediaType == null) {
            return Optional.empty();
        }
        MediaTypeDecoder decoder = decodersByType.get(mediaType);
        if(decoder == null) {
            decoder = decodersByType.get(mediaType.getExtension());
        }
        return Optional.ofNullable(decoder);
    }
}
