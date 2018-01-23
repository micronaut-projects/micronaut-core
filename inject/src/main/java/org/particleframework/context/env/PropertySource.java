package org.particleframework.context.env;

import org.particleframework.core.order.Ordered;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A PropertySource is a location to resolve property values from. The property keys are are available via the {@link #iterator()} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySource extends Iterable<String>, Ordered {
    /**
     * @return The name of the property source
     */
    String getName();
    /**
     * Get a property value of the given key
     *
     * @param key The key
     *
     * @return The value
     */
    Object get(String key);

    /**
     * @return Whether the property source has upper case under score separated keys
     */
    default PropertyConvention getConvention() {
        return PropertyConvention.LOWER_CASE_DOT_SEPARATED;
    }

    /**
     * Create a {@link PropertySource} from the given map
     *
     * @param name The name of the property source
     * @param map The map
     * @return The {@link PropertySource}
     */
    static PropertySource of(String name, Map<String, Object> map) {
        return new MapPropertySource(name, map);
    }
    /**
     * Create a {@link PropertySource} named {@link Environment#DEFAULT_NAME} from the given map
     *
     * @param map The map
     * @return The {@link PropertySource}
     */
    static PropertySource of(Map<String, Object> map) {
        return new MapPropertySource(Environment.DEFAULT_NAME, map);
    }


    enum PropertyConvention {
        /**
         * Upper case separated by under scores (environment variable style)
         */
        UPPER_CASE_UNDER_SCORE_SEPARATED,
        /**
         * Lower case separated by dots (java properties file style)
         */
        LOWER_CASE_DOT_SEPARATED
    }
}
