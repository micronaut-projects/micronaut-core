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
package io.micronaut.runtime.http.codec;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import javax.inject.Singleton;
import java.util.List;

/**
 * A bean for the default {@link MediaTypeCodecRegistry} used by the server.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@BootstrapContextCompatible
public class MediaTypeCodecRegistryFactory {

    /**
     * @param codecs List of codecs for media types
     * @return A bean for default codecs registry
     */
    @Singleton
    @Primary
    @BootstrapContextCompatible
    MediaTypeCodecRegistry mediaTypeCodecRegistry(List<MediaTypeCodec> codecs) {
        return MediaTypeCodecRegistry.of(codecs);
    }
}
