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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.value.PropertyResolver;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>Defines a singleton bean whose property values are resolved from a {@link PropertyResolver}.</p>
 * <p>
 * <p>The {@link PropertyResolver} is typically the Micronaut {@link io.micronaut.context.env.Environment}.</p>
 * <p>
 * <p>The {@link #value()} of the annotation is used to indicate the prefix where the configuration properties are located.
 * The class can define properties or fields which will have the configuration properties to them at runtime.
 * </p>
 * <p>
 * <p>Complex nested properties are supported via classes that are public static inner classes and are also annotated
 * with {@link ConfigurationProperties}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@ConfigurationReader
public @interface ConfigurationProperties {

    /**
     * @return The prefix to use to resolve the properties
     */
    @AliasFor(annotation = ConfigurationReader.class, member = "value")
    String value();

    /**
     * <p>If the properties of this configuration can also be resolved from the CLI a prefix can be specified.</p>
     * <p>
     * <p>For example given a prefix value {code server-} and a property called {code port}, Micronaut will attempt
     * to resolve the value of --server-port when specified on the command line</p>
     *
     * @return The CLI prefix of the configuration. If a blank string is used then no prefix is appended
     */
    String[] cliPrefix() default {};

}
