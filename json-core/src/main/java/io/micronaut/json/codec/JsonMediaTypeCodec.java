/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.json.codec;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.List;

/**
 * A {@link io.micronaut.http.codec.MediaTypeCodec} for JSON and Jackson.
 *
 * @author Graeme Rocher
 * @since 1.0.0
 * @deprecated Replaced with message body writers / readers API
 */
@Experimental
@Named(MapperMediaTypeCodec.REGULAR_JSON_MEDIA_TYPE_CODEC_NAME)
@Singleton
@BootstrapContextCompatible
@Deprecated(forRemoval = true, since = "4.7")
public class JsonMediaTypeCodec extends MapperMediaTypeCodec {

    public static final String CONFIGURATION_QUALIFIER = "json";

    public static final List<MediaType> JSON_ADDITIONAL_TYPES = Arrays.asList(
        MediaType.TEXT_JSON_TYPE,
        MediaType.APPLICATION_HAL_JSON_TYPE,
        MediaType.APPLICATION_JSON_GITHUB_TYPE,
        MediaType.APPLICATION_JSON_FEED_TYPE,
        MediaType.APPLICATION_JSON_PATCH_TYPE,
        MediaType.APPLICATION_JSON_MERGE_PATCH_TYPE,
        MediaType.APPLICATION_JSON_PROBLEM_TYPE,
        MediaType.APPLICATION_JSON_SCHEMA_TYPE
    );

    /**
     * @param jsonMapper To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration The configuration for the codec
     */
    public JsonMediaTypeCodec(JsonMapper jsonMapper,
                              ApplicationConfiguration applicationConfiguration,
                              @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        this(() -> jsonMapper, applicationConfiguration, codecConfiguration);
    }

    /**
     * @param jsonCodec To read/write JSON
     * @param applicationConfiguration The common application configurations
     * @param codecConfiguration The configuration for the codec
     */
    @Inject
    public JsonMediaTypeCodec(BeanProvider<JsonMapper> jsonCodec,
                              ApplicationConfiguration applicationConfiguration,
                              @Named(CONFIGURATION_QUALIFIER) @Nullable CodecConfiguration codecConfiguration) {
        super(jsonCodec, applicationConfiguration, codecConfiguration, MediaType.APPLICATION_JSON_TYPE, JSON_ADDITIONAL_TYPES);
    }

    @Override
    protected MapperMediaTypeCodec cloneWithMapper(JsonMapper mapper) {
        return new JsonMediaTypeCodec(mapper, applicationConfiguration, codecConfiguration);
    }
}
