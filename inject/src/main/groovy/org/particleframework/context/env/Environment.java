package org.particleframework.context.env;

import org.particleframework.config.PropertyResolver;
import org.particleframework.context.LifeCycle;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.io.ResourceLoader;
import org.particleframework.core.reflect.ClassUtils;

import java.io.InputStream;
import java.util.Optional;

/**
 * The current application environment
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Environment extends PropertyResolver, LifeCycle<Environment>, ConversionService<Environment>, ResourceLoader {

    /**
     * @return The name of the environment
     */
    String getName();

    /**
     * Adds a property source to this environment
     *
     * @param propertySource The property source
     * @return This environment
     */
    Environment addPropertySource(PropertySource propertySource);

    /**
     * @return The class loader for the environment
     */
    ClassLoader getClassLoader();

    /**
     * Check whether the given class is present within this environment
     *
     * @param className The class name
     * @return True if it is
     */
    default boolean isPresent(String className) {
        return ClassUtils.isPresent(className, getClassLoader());
    }

    @Override
    default Optional<InputStream> getResourceAsStream(String path) {
        InputStream inputStream = getClassLoader().getResourceAsStream(path);
        if(inputStream != null) {
            return Optional.of(inputStream);
        }
        return Optional.empty();
    }
}
