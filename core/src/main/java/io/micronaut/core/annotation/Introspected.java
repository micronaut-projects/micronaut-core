/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.core.annotation;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that indicates a type should produce introspection results at compilation time.
 *
 * @author graemerocher
 * @since 1.1
 * @see io.micronaut.core.beans.BeanIntrospection
 * @see io.micronaut.core.beans.BeanIntrospector
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Introspected {

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
     * The annotation types that if present on the property cause the property to be ignored in
     * introspection results.
     *
     * @return The annotation types
     */
    Class<? extends Annotation>[] ignored() default {};

    /**
     * The annotation types that should be indexed for lookup via {@link io.micronaut.core.beans.BeanIntrospection#getProperties(Class)}.
     *
     * @return The indexed annotation types
     */
    Class<? extends Annotation>[] indexed() default {};

    /**
     * Whether annotation metadata should be included in the inspection results.
     *
     * @return True if annotation metadata should be included.
     */
    boolean annotationMetadata() default true;
}
