/*
 * Copyright 2017-2025 original authors
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define a mixin in the compile-time processing context. A mixin allows
 * adding new annotations to an existing class.
 * <p>
 * This annotation is marked as experimental and is subject to change or removal in future versions.
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Experimental
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Mixin {

    /**
     * @return The target of the mixin
     */
    Class<?> value();

    /**
     * A way to specify the target class if it's not accessible. In that case the value should be Object.class.
     *
     * @return The target of the mixin
     */
    String target() default "";

    /**
     * Filters which annotations are included. The predicate will use {@link String#startsWith(String)} to verify if the annotation name should be included.
     *
     * @return The full annotation name or a package to check if the annotation should be included.
     */
    @AliasFor(annotation = Filter.class, member = "includeAnnotations")
    String[] includeAnnotations() default {};

    /**
     * Opposite of {@link #includeAnnotations()}. Filters which annotations to exclude.
     *
     * @return The full annotation name or a package to check if the annotation should not be excluded.
     */
    @AliasFor(annotation = Filter.class, member = "excludeAnnotations")
    String[] excludeAnnotations() default {};

    /**
     * Remove the annotation from the target element. The predicate will use {@link String#startsWith(String)} to verify if the annotation name should be removed.
     *
     * @return The full annotation name or a package to check if the annotation should not be removed.
     */
    @AliasFor(annotation = Filter.class, member = "removeAnnotations")
    String[] removeAnnotations() default {};

    @Experimental
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.SOURCE)
    @interface Filter {

        /**
         * Filters which annotations are included. The predicate will use {@link String#startsWith(String)} to verify if the annotation name should be included.
         *
         * @return The full annotation name or a package to check if the annotation should be included.
         */
        String[] includeAnnotations() default {};

        /**
         * Opposite of {@link #includeAnnotations()}. Filters which annotations to exclude.
         *
         * @return The full annotation name or a package to check if the annotation should not be excluded.
         */
        String[] excludeAnnotations() default {};

        /**
         * Remove the annotation from the target element. The predicate will use {@link String#startsWith(String)} to verify if the annotation name should be removed.
         *
         * @return The full annotation name or a package to check if the annotation should not be removed.
         */
        String[] removeAnnotations() default {};

    }

}
