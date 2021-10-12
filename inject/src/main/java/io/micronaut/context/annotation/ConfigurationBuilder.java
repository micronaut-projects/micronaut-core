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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>An annotation applicable to a field or method of a {@link ConfigurationProperties} instance that allows to
 * customize the behaviour of properties that are builders themselves.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface ConfigurationBuilder {

    /**
     * @return The configuration prefix
     * @see #configurationPrefix()
     */
    String value() default "";

    /**
     * <p>The default is for {@link ConfigurationBuilder} to look for public JavaBean-style setters. Many APIs however
     * use a builder-style or other style to for constructing configuration.</p>
     * <p>
     * <p>This method allows overriding this behaviour. For example if the builder you are authoring for prefixes write
     * operations with the word "with" by setting the value of this attribute to "with" you can process methods such
     * as {@code withDebug(true)}</p>
     *
     * @return The write prefix to use
     */
    String[] prefixes() default "set";

    /**
     * <p>When this annotation is used, by default the name of the field or method is not taken into account when
     * retrieving configuration properties.</p>
     * <p>
     * <p>This method allows overriding this behaviour. For example if the annotation is placed on a field, you can
     * change the prefix for which configuration values will be searched to populate the field.</p>
     *
     * @return The configuration prefix to use
     */
    @AliasFor(member = "value")
    String configurationPrefix() default "";

    /**
     * <p>Some APIs allow zero argument setters to set boolean flags such as {@code setDebug()}. These by default are
     * not processed unless the value of this annotation is set to true.</p>
     * <p>
     * <p>Note that this attribute works in conjunction with {@link #prefixes()} to allow other styles such as
     * {@code withDebug()}</p>
     *
     * @return True if zero arg setters should be processed
     */
    boolean allowZeroArgs() default false;

    /**
     * Only to be used if the builder is not being created manually.
     *
     * @return The name of a static factory method used to create the builder
     */
    String factoryMethod() default "";

    /**
     * @return The names of the properties that are child builders
     */
    String[] children() default {};

    /**
     * @return The names of the properties to include
     */
    String[] includes() default {};

    /**
     * @return The names of the properties to exclude
     */
    String[] excludes() default {};
}
