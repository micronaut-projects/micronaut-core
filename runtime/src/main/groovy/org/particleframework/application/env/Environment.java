package org.particleframework.application.env;

import org.particleframework.config.PropertyResolver;

/**
 * The current application environment
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Environment extends PropertyResolver {

    /**
     * @return The name of the environment
     */
    String getName();
}
