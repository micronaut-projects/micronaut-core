package org.particleframework.configuration.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.util.CollectionUtils;

import java.util.*;

/**
 * Configuration for the Jackson JSON parser
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("jackson")
public class JacksonConfiguration {

    protected String dateFormat;
    protected Locale locale;
    protected TimeZone timeZone;
    protected Map<SerializationFeature, Boolean> serialization = Collections.emptyMap();
    protected Map<DeserializationFeature, Boolean> deserialization = Collections.emptyMap();
    protected Map<MapperFeature, Boolean> mapper = Collections.emptyMap();
    protected Map<JsonParser.Feature, Boolean> parser = Collections.emptyMap();
    protected Map<JsonGenerator.Feature, Boolean> generator = Collections.emptyMap();
    protected Set<JsonInclude.Include> serializationInclusion = CollectionUtils.setOf(JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_EMPTY);

    /**
     * @return The default serialization inclusion settings
     */
    public Set<JsonInclude.Include> getSerializationInclusion() {
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
}
