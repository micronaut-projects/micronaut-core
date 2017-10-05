package org.particleframework.context.env;

import org.particleframework.core.order.Ordered;

import java.util.Map;

/**
 * A PropertySource is a location to resolve property values from. The property keys are are available via the {@link #iterator()} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySource extends Iterable<String>, Ordered {
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
    default boolean hasUpperCaseKeys() {
        return false;
    }

    /**
     * Create a {@link PropertySource} from the given map
     *
     * @param map The map
     * @return The {@link PropertySource}
     */
    static PropertySource of(Map<String, Object> map) {
        return new MapPropertySource(map);
    }
}
