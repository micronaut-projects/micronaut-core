package org.particleframework.context.env;

import org.particleframework.core.util.Toggleable;

import java.util.Optional;

/**
 * Loads the given property source for the given environment
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySourceLoader extends Toggleable {

    String DEFAULT_NAME = "application";

    /**
     * Load a {@link PropertySource} for the given {@link Environment}
     *
     * @param environment The environment
     * @return An optional of {@link PropertySource}
     */
    default Optional<PropertySource> load(Environment environment) {
        return load(DEFAULT_NAME, environment);
    }

    /**
     * Load a {@link PropertySource} for the given {@link Environment}
     *
     * @param name The name of the resource to loader
     * @param environment The environment
     * @return An optional of {@link PropertySource}
     */
    Optional<PropertySource> load(String name, Environment environment);
}
