package org.particleframework.context.annotation;

import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.TrueCondition;

import java.lang.annotation.*;

/**
 * Expresses a requirement for a bean or {@link Configuration}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Repeatable(Requirements.class)
public @interface Requires {

    /**
     * Expresses that the configuration will only load within the given environments
     *
     * @return The names of the environments this configuration will load in
     */
    String[] env() default {};

    /**
     * Expresses that the given property should be set for the bean to load. By default the value of the property
     * should be "yes", "YES", "true", "TRUE", "y" or "Y" for it to be considered to be set. If a different value is to be used then
     * the {@link #value()} method should be used
     *
     * @return The property that should be set.
     */
    String property() default "";

    /**
     * One ore more custom condition classes
     *
     * @return The condition classes
     */
    Class<? extends Condition> condition() default TrueCondition.class;

    /**
     * Used to express an SDK requirement. Typically used in combination with {@link #version()} to specify a minimum version requirement
     *
     * @return The SDK required
     */
    Sdk sdk() default Sdk.PARTICLE;
    /**
     * Expresses the configurations that should be present for the bean or configuration to load
     *
     * @return The configurations
     */
    String configuration() default "";

    /**
     * Used in combination with {@link #property()} to express the required value of the property
     *
     * @return THe required value
     */
    String value() default "";

    /**
     * Used in combination with {@link #configuration()}, {@link #classes()} or {@link #beans()} to express a minimum version of the configuration or classes
     *
     * @return The minimum version
     */
    String version() default "";

    /**
     * Expresses the given classes that should be present on the classpath for the bean or configuration to load
     *
     * @return The classes
     */
    Class[] classes() default {};

    /**
     * Expresses that beans of the given types should be available for the bean or configuration to load
     *
     * @return The beans
     */
    Class[] beans() default {};

    /**
     * Expresses the given classes that should be missing from the classpath for the bean or configuration to load
     *
     * @return The classes
     */
    Class[] missing() default {};

    /**
     * Expresses the given beans that should be missing from the classpath for the bean or configuration to load
     *
     * @return The classes
     */
    Class[] missingBeans() default {};

    /**
     * Expresses the given configurations that should be missing from the classpath for the bean or configuration to load
     *
     * @return The classes
     */
    String[] missingConfigurations() default {};

    /**
     * Expresses that the bean or configuration will only be configured if the given property is missing
     *
     * @return The property or properties that should be missing
     */
    String missingProperty() default "";

    /**
     * Used to express a required SDK version
     */
    enum Sdk {
        JAVA,
        GROOVY,
        PARTICLE
    }

}
