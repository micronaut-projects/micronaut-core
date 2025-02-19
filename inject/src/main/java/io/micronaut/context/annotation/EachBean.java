/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.annotation;

import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>This annotation allows driving the production of {@link Bean} definitions from presence of other bean definitions.
 * Typically used in conjunction with {@link EachProperty}</p>
 *
 * <p>For example:</p>
 * <pre><code>
 *  {@literal @}EachProperty("foo.bar")
 *   public class ExampleConfiguration {
 *   }
 * </code></pre>
 *
 * <p>In the above example a new {@code ExampleConfiguration} bean will be created for each item under the
 * {@code foo.bar} key in application configuration</p>
 *
 * <p>One can then drive the configuration of other beans with the same annotation:</p>
 * <pre><code>
 *  {@literal @}EachBean(ExampleConfiguration)
 *  {@literal @}Singleton
 *   public class ExampleBean {
 *      ExampleBean(ExampleConfiguration config) {
 *          ...
 *      }
 *   }
 * </code></pre>
 * <p>
 * {@link EachBean} requires that the parent bean has a {@link jakarta.inject.Named} qualifier, since the qualifier is inherited by each bean created by {@link EachBean}.
 *
 * @author Graeme Rocher
 * @see EachProperty
 * @see ConfigurationProperties
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Singleton
public @interface EachBean {

    /**
     * @return The bean type that this bean is driven by
     */
    Class<?> value();

    /**
     * Enable for a new bean definition to inherit the generics from the driven bean definitions.
     *
     * @return The remap configuration
     * @since 4.6
     */
    RemapGeneric[] remapGenerics() default {};


    /**
     * The generics remapping configuration.
     *
     * @author Denis Stepanov
     * @since 4.6
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface RemapGeneric {

        /**
         * @return The tape of which does these generics belong.
         */
        Class<?> type();

        /**
         * @return The name of the generic defined on the driven bean.
         */
        String name();

        /**
         * @return The name of the generic on the produced bean. If not specified the same name will be used.
         */
        String to() default "";

    }
}
