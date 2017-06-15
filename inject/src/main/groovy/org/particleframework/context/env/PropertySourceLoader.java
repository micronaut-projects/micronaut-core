package org.particleframework.context.env;

import java.util.Optional;

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
     * @return An optional of {@link PropertySource}
     */
    Optional<PropertySource> load(Environment environment);
}
