package org.particleframework.application.env;

/**
 * Loads the given property source for the given environment
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySourceLoader {

    /**
     * Load a {@link PropertySource} for the given {@link Environment}
     *
     * @param environment The environment
     * @return The property source
     */
    PropertySource load(Environment environment);
}
