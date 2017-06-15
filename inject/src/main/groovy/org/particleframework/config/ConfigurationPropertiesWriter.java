package org.particleframework.config;

import org.particleframework.config.ConfigurationProperties;
import org.particleframework.inject.writer.BeanDefinitionWriter;

/**
 * <p>A specialization of the {@link BeanDefinitionWriter} class that constructs a class annotated with {@link ConfigurationProperties}</p>
 *
 * <p>This implementation will populate calls to {@link org.particleframework.config.PropertyResolver#getProperty(String, Class)} for each field or property of the class that isn't annotated with {@link javax.inject.Inject}</p>
 *
 * <p>Also will handle nested static inner classes as sub properties</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConfigurationPropertiesWriter extends BeanDefinitionWriter {
    public ConfigurationPropertiesWriter(String packageName, String className) {
        super(packageName, className, ConfigurationProperties.class.getName(), true);
    }
}
