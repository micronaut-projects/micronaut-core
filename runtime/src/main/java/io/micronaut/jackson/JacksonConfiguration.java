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
import io.micronaut.core.util.CollectionUtils;

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

    private String dateFormat;
    private Locale locale;
    private TimeZone timeZone;
    private int arraySizeThreshold = 100;
    private Map<SerializationFeature, Boolean> serialization = Collections.emptyMap();
    private Map<DeserializationFeature, Boolean> deserialization = Collections.emptyMap();
    private Map<MapperFeature, Boolean> mapper = Collections.emptyMap();
    private Map<JsonParser.Feature, Boolean> parser = Collections.emptyMap();
    private Map<JsonGenerator.Feature, Boolean> generator = Collections.emptyMap();
    private JsonInclude.Include serializationInclusion = JsonInclude.Include.NON_EMPTY;

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

    /**
     * Sets the default date format to use.
     * @param dateFormat The date format
     */
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    /**
     * Sets the locale to use.
     * @param locale The locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * Sets the timezone to use.
     * @param timeZone The timezone
     */
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Sets the array size threshold for data binding.
     * @param arraySizeThreshold The array size threshold
     */
    public void setArraySizeThreshold(int arraySizeThreshold) {
        this.arraySizeThreshold = arraySizeThreshold;
    }

    /**
     * Sets the serialization features to use.
     * @param serialization The serialization features.
     */
    public void setSerialization(Map<SerializationFeature, Boolean> serialization) {
        if (CollectionUtils.isNotEmpty(serialization)) {
            this.serialization = serialization;
        }
    }

    /**
     * Sets the deserialization features to use.
     * @param deserialization The deserialiation features.
     */
    public void setDeserialization(Map<DeserializationFeature, Boolean> deserialization) {
        if (CollectionUtils.isNotEmpty(deserialization)) {
            this.deserialization = deserialization;
        }
    }

    /**
     * Sets the object mapper features to use.
     * @param mapper The object mapper features
     */
    public void setMapper(Map<MapperFeature, Boolean> mapper) {
        if (CollectionUtils.isNotEmpty(mapper)) {
            this.mapper = mapper;
        }
    }

    /**
     * Sets the parser features to use.
     * @param parser The parser features
     */
    public void setParser(Map<JsonParser.Feature, Boolean> parser) {
        if (CollectionUtils.isNotEmpty(parser)) {
            this.parser = parser;
        }
    }

    /**
     * Sets the generator features to use.
     * @param generator The generator features
     */
    public void setGenerator(Map<JsonGenerator.Feature, Boolean> generator) {
        if (CollectionUtils.isNotEmpty(generator)) {
            this.generator = generator;
        }
    }

    /**
     * Sets the serialization inclusion mode.
     *
     * @param serializationInclusion The serialization inclusion mode
     */
    public void setSerializationInclusion(JsonInclude.Include serializationInclusion) {
        if (serializationInclusion != null) {
            this.serializationInclusion = serializationInclusion;
        }
    }
}
