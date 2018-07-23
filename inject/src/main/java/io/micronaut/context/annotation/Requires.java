/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.context.annotation;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.TrueCondition;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Expresses a requirement for a bean or {@link Configuration}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Repeatable(Requirements.class)
public @interface Requires {

    /**
     * Expresses that the configuration will only load within the given environments.
     *
     * @return The names of the environments this configuration will load in
     */
    String[] env() default {};

    /**
     * Expresses that the configuration will not load within the given environments.
     *
     * @return The names of the environments this configuration will not load in
     */
    String[] notEnv() default {};

    /**
     * Expresses that the given property should be set for the bean to load. By default the value of the property
     * should be "yes", "YES", "true", "TRUE", "y" or "Y" for it to be considered to be set. If a different value is
     * to be used then the {@link #value()} method should be used.
     *
     * @return The property that should be set.
     */
    String property() default "";

    /**
     * Constraint a property to not equal the given value.
     *
     * @return The value to not equal
     */
    String notEquals() default "";

    /**
     * One ore more custom condition classes.
     *
     * @return The condition classes
     */
    Class<? extends Condition> condition() default TrueCondition.class;

    /**
     * Used to express an SDK requirement. Typically used in combination with {@link #version()} to specify a minimum
     * version requirement.
     *
     * @return The SDK required
     */
    Sdk sdk() default Sdk.MICRONAUT;

    /**
     * Expresses the configurations that should be present for the bean or configuration to load.
     *
     * @return The configurations
     */
    String configuration() default "";

    /**
     * Used in combination with {@link #property()} to express the required value of the property.
     *
     * @return The required value
     */
    String value() default "";

    /**
     * @return The default value if no value is specified
     */
    String defaultValue() default "";

    /**
     * Used in combination with {@link #property()} to express the required pattern of the property. The
     * pattern will be evaluated with {@link String#matches(String)}.
     *
     * @return The required pattern
     */
    String pattern() default "";

    /**
     * Used in combination with {@link #sdk()}, {@link #configuration()}, {@link #classes()} or {@link #beans()} to
     * express a minimum version of the SDK, configuration or classes.
     *
     * @return The minimum version
     */
    String version() default "";

    /**
     * Expresses the given classes that should be present on the classpath for the bean or configuration to load.
     *
     * @return The classes
     */
    Class[] classes() default {};

    /**
     * Expresses that the configuration requires entities annotated with the given annotations to be available to the
     * application via package scanning.
     *
     * @return The classes
     */
    Class<? extends Annotation>[] entities() default {};


    /**
     * Expresses that beans of the given types should be available for the bean or configuration to load.
     *
     * @return The beans
     */
    Class[] beans() default {};

    /**
     * Expresses the given classes that should be missing from the classpath for the bean or configuration to load.
     *
     * @return The classes
     */
    Class[] missing() default {};

    /**
     * Expresses the given beans that should be missing from the classpath for the bean or configuration to load.
     *
     * @return The classes
     */
    Class[] missingBeans() default {};

    /**
     * Expresses the given configurations that should be missing from the classpath for the bean or configuration to
     * load.
     *
     * @return The classes
     */
    String[] missingConfigurations() default {};

    /**
     * Expresses that the bean or configuration will only be configured if the given property is missing.
     *
     * @return The property or properties that should be missing
     */
    String missingProperty() default "";

    /**
     * Used to express a required SDK version.
     */
    enum Sdk {
        JAVA,
        GROOVY,
        MICRONAUT
    }
}
