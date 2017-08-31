package org.particleframework.configuration.jackson;

import org.particleframework.config.ConfigurationProperties;

/**
 * Configuration for the Jackson JSON parser
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("jackson")
public class JacksonConfiguration {

    private String dateFormat;

    /**
     * @return The date format to use for dates
     */
    public String getDateFormat() {
        return dateFormat;
    }
}
