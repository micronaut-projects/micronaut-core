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

package io.micronaut.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Configuration for the Jackson JSON parser.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("jackson")
public class JacksonConfiguration {

    protected String dateFormat;
    protected Locale locale;
    protected TimeZone timeZone;
    protected int arraySizeThreshold = 100;
    protected Map<SerializationFeature, Boolean> serialization = Collections.emptyMap();
    protected Map<DeserializationFeature, Boolean> deserialization = Collections.emptyMap();
    protected Map<MapperFeature, Boolean> mapper = Collections.emptyMap();
    protected Map<JsonParser.Feature, Boolean> parser = Collections.emptyMap();
    protected Map<JsonGenerator.Feature, Boolean> generator = Collections.emptyMap();
    protected JsonInclude.Include serializationInclusion = JsonInclude.Include.NON_EMPTY;

    /**
     * @return The default serialization inclusion settings
     */
    public JsonInclude.Include getSerializationInclusion() {
        return serializationInclusion;
    }

    /**
     * @return The default locale to use
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * @return The default time zone to use
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * @return The date format to use for dates
     */
    public String getDateFormat() {
        return dateFormat;
    }

    /**
     * @return The serialization settings
     */
    public Map<SerializationFeature, Boolean> getSerializationSettings() {
        return serialization;
    }

    /**
     * @return The deserialization settings
     */
    public Map<DeserializationFeature, Boolean> getDeserializationSettings() {
        return deserialization;
    }

    /**
     * @return Settings for the object mapper
     */
    public Map<MapperFeature, Boolean> getMapperSettings() {
        return mapper;
    }

    /**
     * @return Settings for the parser
     */
    public Map<JsonParser.Feature, Boolean> getParserSettings() {
        return parser;
    }

    /**
     * @return Settings for the generator
     */
    public Map<JsonGenerator.Feature, Boolean> getGeneratorSettings() {
        return generator;
    }

    /**
     * @return The array size threshold to use when using Jackson for data binding
     */
    public int getArraySizeThreshold() {
        return arraySizeThreshold;
    }
}
