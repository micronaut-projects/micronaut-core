package org.particleframework.application.context;

import org.particleframework.application.env.Environment;
import org.particleframework.context.DefaultBeanContext;
import org.particleframework.core.convert.ConversionService;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ApplicationContext {
    @Override
    public ConversionService getConversionService() {
        return null;
    }

    @Override
    public Environment getEnvironment() {
        return null;
    }

    @Override
    public ApplicationContext start() {
        return (ApplicationContext) super.start();
    }

    @Override
    public ApplicationContext stop() {
        return (ApplicationContext) super.stop();
    }
}
