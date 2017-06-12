package org.particleframework.application.env;

import org.particleframework.config.PropertyResolver;
import org.particleframework.context.LifeCycle;
import org.particleframework.core.convert.ConversionService;

/**
 * The current application environment
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Environment extends PropertyResolver, LifeCycle<Environment>, ConversionService {

    /**
     * @return The name of the environment
     */
    String getName();
}
