/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.annotation;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation that models directly the GraalVM reflect-config.json format.
 *
 * @since 3.5.0
 * @author graemerocher
 * @see io.micronaut.core.graal.GraalReflectionConfigurer
 */
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ReflectionConfig.ReflectionConfigList.class)
public @interface ReflectionConfig {
    /**
     * @return The type the config applies to.
     */
    Class<?> type();

    /**
     * @return Enum representing the access type.
     */
    TypeHint.AccessType[] accessType() default {};

    /**
     * @return The methods.
     */
    ReflectiveMethodConfig[] methods() default {};

    /**
     * @return The methods.
     */
    ReflectiveFieldConfig[] fields() default {};

    /**
     * Method configuration.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface ReflectiveMethodConfig {
        /**
         * @return The name of the method.
         */
        String name();

        /**
         * @return The parameter types.
         */
        Class<?>[] parameterTypes() default {};
    }

    /**
     * Field configuration.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface ReflectiveFieldConfig {
        /**
         * @return The name of the method.
         */
        String name();
    }

    /**
     * Wrapper annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface ReflectionConfigList {
        /**
         * @return The config definitions.
         */
        ReflectionConfig[] value();
    }
}
