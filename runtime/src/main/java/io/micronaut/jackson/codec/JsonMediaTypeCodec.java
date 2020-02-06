/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * A {@link io.micronaut.http.codec.MediaTypeCodec} for JSON and Jackson.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 */
@Named("json")
@Singleton
@BootstrapContextCompatible
public class JsonMediaTypeCodec extends JacksonMediaTypeCodec {

    public static final String CONFIGURATION_QUALIFIER = "json";

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     */
    public JsonMediaTypeCodec(ObjectMapper objectMapper,
                              ApplicationConfiguration applicationConfiguration,
                              @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        super(objectMapper, applicationConfiguration, codecConfiguration, MediaType.APPLICATION_JSON_TYPE);
    }

    /**
     * @param objectMapper             To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration       The configuration for the codec
     */
    @Inject
    public JsonMediaTypeCodec(Provider<ObjectMapper> objectMapper,
                              ApplicationConfiguration applicationConfiguration,
                              @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        super(objectMapper, applicationConfiguration, codecConfiguration, MediaType.APPLICATION_JSON_TYPE);
    }

    @Override
    public JacksonMediaTypeCodec cloneWithFeatures(JacksonFeatures jacksonFeatures) {
        ObjectMapper objectMapper = getObjectMapper().copy();
        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);

        return new JsonMediaTypeCodec(objectMapper, applicationConfiguration, codecConfiguration);
    }
}
