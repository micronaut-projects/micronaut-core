package org.particleframework.config;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines a singleton bean whose property values are resolved from a {@link PropertyResolver}
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
