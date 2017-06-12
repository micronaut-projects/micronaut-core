package org.particleframework.application.env;

/**
 * A PropertySource is a location to resolve property values from. The property keys are are available via the {@link #iterator()} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySource extends Iterable<String> {
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
}
