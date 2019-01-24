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

package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

import static io.micronaut.jackson.codec.JsonMediaTypeCodec.CONFIGURATION_QUALIFIER;

/**
 * A factory to produce {@link io.micronaut.http.codec.MediaTypeCodec} for JSON and Jackson using a specified
 * JsonView class.
 */
@Requires(beans = JacksonConfiguration.class)
@Requires(property = "jackson.json-view-enabled")
@Singleton
public class JsonViewMediaTypeCodecFactory {

    private final ObjectMapper objectMapper;
    private final ApplicationConfiguration applicationConfiguration;
    private final CodecConfiguration codecConfiguration;

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     */
    public JsonViewMediaTypeCodecFactory(ObjectMapper objectMapper,
                              ApplicationConfiguration applicationConfiguration,
                              @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this.objectMapper = objectMapper;
        this.applicationConfiguration = applicationConfiguration;
        this.codecConfiguration = codecConfiguration;
    }

    /**
     * Creates a {@link JsonMediaTypeCodec} for the view class (specified as the JsonView annotation value).
     * @param viewClass The view class
     * @return The codec
     */
    public JsonMediaTypeCodec createJsonViewCodec(Class<?> viewClass) {
        ObjectMapper viewMapper = objectMapper.copy();
        viewMapper.setConfig(viewMapper.getSerializationConfig().withView(viewClass));
        return new JsonMediaTypeCodec(viewMapper, applicationConfiguration, codecConfiguration);
    }
}
