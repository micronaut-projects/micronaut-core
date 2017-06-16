package org.particleframework.context.env;

import org.particleframework.config.PropertyResolver;
import org.particleframework.context.LifeCycle;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.io.ResourceLoader;
import org.particleframework.core.io.scan.ClassPathAnnotationScanner;
import org.particleframework.core.reflect.ClassUtils;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
     * Add an application package. Application packages are candidates for scanning for tools that need it (such as JPA or GORM)
     *
     * @param pkg The package to add
     * @return This environment
     */
    default Environment addPackage(Package pkg) {
        addPackage(pkg.getName());
        return this;
    }

    /**
     * Add an application package. Application packages are candidates for scanning for tools that need it (such as JPA or GORM)
     *
     * @param pkg The package to add
     * @return This environment
     */
    Environment addPackage(String pkg);

    /**
     * @return The application packages
     */
    Collection<String> getPackages();

    /**
     * Scan the current environment for classes annotated with the given annotation. Use with care, repeated
     * invocations should be avoided for performance reasons.
     *
     * @param annotation The annotation to scan
     * @return The classes
     */
    default Stream<Class> scan(Class<? extends Annotation> annotation) {
        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner(getClassLoader());
        return scanner.scan(annotation, getPackages());
    }

    /**
     * @return The class loader for the environment
     */
    default ClassLoader getClassLoader() {
        return Environment.class.getClassLoader();
    }

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
