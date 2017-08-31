package org.particleframework.configuration.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.particleframework.config.ConfigurationProperties;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for the Jackson JSON parser
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("jackson")
public class JacksonConfiguration {

    private String dateFormat;
    private Map<SerializationFeature, Boolean> serialization = Collections.emptyMap();
    private Map<DeserializationFeature, Boolean> deserialization = Collections.emptyMap();

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
}
