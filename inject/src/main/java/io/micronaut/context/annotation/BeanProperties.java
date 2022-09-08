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

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Bean properties configuration annotation.
 * The annotation allows to change different options how beans should be detected.
 * This configuration is used directly to produce bean properties for:
 * introspection, configuration reader and configuration factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
@Documented
@Retention(SOURCE)
@Inherited
public @interface BeanProperties {

    String ACCESS_KIND = "accessKind";
    String VISIBILITY = "visibility";
    String INCLUDES = "includes";
    String EXCLUDES = "excludes";
    String ALLOW_WRITE_WITH_ZERO_ARGS = "allowWriteWithZeroArgs";
    String ALLOW_WRITE_WITH_MULTIPLE_ARGS = "allowWriteWithMultipleArgs";
    String EXCLUDED_ANNOTATIONS = "excludedAnnotations";

    /**
     * <p>The default access type is {@link AccessKind#METHOD} which treats only public JavaBean getters or Java record components as properties. By specifying {@link AccessKind#FIELD}, public or package-protected fields will be used instead. </p>
     *
     * <p>If both {@link AccessKind#FIELD} and {@link AccessKind#METHOD} are specified then the order as they appear in the annotation will be used to determine whether the field or method will be used in the case where both exist.</p>
     *
     * @return The access type. Defaults to {@link AccessKind#METHOD}
     */
    AccessKind[] accessKind() default {AccessKind.METHOD};

    /**
     * Allows specifying the visibility policy to use to control which fields and methods are included.
     *
     * @return The visibility policies
     */
    Visibility[] visibility() default {Visibility.DEFAULT};

    /**
     * The property names to include. Defaults to all properties.
     *
     * @return The names of the properties
     */
    String[] includes() default {};

    /**
     * The property names to excludes. Defaults to excluding none.
     *
     * @return The names of the properties
     */
    String[] excludes() default {};

    /**
     * <p>Some APIs allow zero argument setters to set boolean flags such as {@code setDebug()}. These by default are
     * not processed unless the value of this annotation is set to true.</p>
     *
     * <p>Note that this attribute works in conjunction with {@link io.micronaut.core.annotation.AccessorsStyle#writePrefixes()} to allow other styles such as
     * {@code withDebug()}</p>
     *
     * @return True if zero arg setters should be processed
     */
    boolean allowWriteWithZeroArgs() default false;

    /**
     * Some APIs allow multiple argument setters to convert the value {@code withDuration(long, TimeUnit)}.
     *
     * @return True if multiple arg setters should be processed
     */
    boolean allowWriteWithMultipleArgs() default false;

    /**
     * The annotation types that if present on the property cause the property to be excluded from results.
     *
     * @return The annotation types
     */
    Class<? extends Annotation>[] excludedAnnotations() default {};

    /**
     * The access type for bean properties.
     */
    enum AccessKind {
        /**
         * Allows the use of public or package-protected fields to represent bean properties.
         */
        FIELD,
        /**
         * The default behaviour which is to favour public getters for bean properties.
         */
        METHOD
    }

    /**
     * Visibility policy for bean properties and fields.
     */
    enum Visibility {

        /**
         * Only public methods and/or fields are included.
         */
        PUBLIC,

        /**
         * The default behaviour which in addition to public getters and setters will also include package protected fields if an {@link BeanProperties.AccessKind} of {@link BeanProperties.AccessKind#FIELD} is specified.
         */
        DEFAULT
    }
}
