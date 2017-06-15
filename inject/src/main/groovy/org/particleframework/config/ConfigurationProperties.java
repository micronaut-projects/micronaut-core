package org.particleframework.config;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Defines a singleton bean whose property values are resolved from a {@link PropertyResolver}.</p>
 *
 * <p>The {@link PropertyResolver} is typically the Particle {@link org.particleframework.context.env.Environment}.</p>
 *
 * <p>The value of the annotation is used to indicate the prefix where the configuration properties are located.
 * The class can define properties or fields which will have the configuration properties to them at runtime.
 * </p>
 *
 * <p>Complex nested properties are supported via classes that are public static inner classes.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigurationProperties {
    /**
     * @return The prefix to use to resolve the properties
     */
    String value();
}
