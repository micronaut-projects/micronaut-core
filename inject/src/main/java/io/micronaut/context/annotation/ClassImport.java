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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows importing an already compiled set of classes and processed them like a non-compiled class.
 *
 * The difference between {@link Import} and this annotation, is that classes added using {@link Import}
 * are not processed using the type visitors.
 *
 * <p>Note that this annotation is likely to require more use of reflection if package protected members require injection.</p>
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Experimental
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface ClassImport {

    /**
     * @return The classes to import.
     */
    Class<?>[] classes() default {};

    /**
     * Alternative way to specify the value for `classes` when the class cannot be referenced.
     *
     * @return The class names to generate introspections for
     */
    @AliasFor(member = "classes")
    String[] classNames() default {};

    /**
     * A list of package names to import.
     *
     * <p>Note when {@link #classes()} or {@link #classNames()} is specified this attribute is ignored</p>
     *
     * @return The packages to import.
     */
    String[] packages() default {};

    /**
     * Annotate every class with the given annotation types.
     *
     * @return The annotations
     */
    Class<? extends Annotation>[] annotate() default {};

    /**
     * Alternative way to specify the value for `annotate` when the class cannot be referenced.
     *
     * @return The annotation names
     */
    @AliasFor(member = "annotate")
    String[] annotateNames() default {};

    /**
     * The annotation types to exclude in a search when specifying the {@link #packages()} attribute (this attribute has no effect when combined with {@link #classes()}).
     *
     * @return The annotation types
     */
    Class<? extends Annotation>[] excludedAnnotations() default {};

    /**
     * The annotation types that if present on the property cause only the properties with the specified annotation to be included in the result.
     *
     * @return The annotation types
     */
    Class<? extends Annotation>[] includedAnnotations() default {};

    /**
     * @return The package to write any kind of generated metadata to. By default, uses the class package.
     */
    String targetPackage() default "";

    /**
     * Repeated wrapper for this annotation.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @interface Repeated {
        ClassImport[] value();
    }
}
