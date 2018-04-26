/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.jackson.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Singleton;

/**
 * A codec for {@link MediaType#APPLICATION_JSON_STREAM}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class JsonStreamMediaTypeCodec extends JsonMediaTypeCodec {

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     */
    public JsonStreamMediaTypeCodec(ObjectMapper objectMapper, ApplicationConfiguration applicationConfiguration) {
        super(objectMapper, applicationConfiguration);
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_JSON_STREAM_TYPE;
    }
}
