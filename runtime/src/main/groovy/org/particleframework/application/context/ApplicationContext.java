package org.particleframework.application.context;

import org.particleframework.application.env.Environment;
import org.particleframework.config.PropertyResolver;
import org.particleframework.context.BeanContext;
import org.particleframework.core.convert.ConversionService;

/**
 * An application context extends a {@link BeanContext} and adds the concepts of configuration, environments and runtimes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ApplicationContext extends BeanContext, PropertyResolver {

    /**
     * @return The default conversion service
     */
    ConversionService getConversionService();

    /**
     * @return The application environment
     */
    Environment getEnvironment();

    /**
     * Starts the application context
     *
     * @return The application context
     */
    @Override
    ApplicationContext start();

    /**
     * Stops the application context
     *
     * @return The application context
     */
    @Override
    ApplicationContext stop();
}
