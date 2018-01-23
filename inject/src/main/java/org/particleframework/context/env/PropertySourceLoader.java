package org.particleframework.context.env;

import org.particleframework.core.util.Toggleable;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Loads the given property source for the given environment
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PropertySourceLoader extends Toggleable, PropertySourceLocator {

    /**
     * Load a {@link PropertySource} for the given {@link Environment}
     *
     * @param environment The environment
     * @return An optional of {@link PropertySource}
     */
    @Override
    default Optional<PropertySource> load(Environment environment) {
        return load(Environment.DEFAULT_NAME, environment, null);
    }

    /**
     * Load a {@link PropertySource} for the given {@link Environment}
     *
     * @param resourceName The resourceName of the resource to load
     * @param environment The environment
     * @param environmentName The environment name to load. Null if the default environment is to be used
     * @return An optional of {@link PropertySource}
     */
    Optional<PropertySource> load(String resourceName, Environment environment, @Nullable String environmentName);
}
