/*
 * Copyright 2017-2026 original authors
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

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests the compile time generation of an {@link AnnotationBuilder} for each of the given annotation types.
 *
 * <p>The builders are supplied through the service loader and found with the {@link AnnotationBuilderRegistry}.
 * A builder creates instances of the annotation type from an {@link AnnotationValue} or from a map of the member
 * values, converting the values to the types of the members, without a {@link java.lang.reflect.Proxy} and without
 * reading the defaults of the annotation type reflectively. The instances are equal to, and hash like, the ones
 * the metadata creates through {@link AnnotationMetadata#synthesize(Class)}.</p>
 *
 * <p>The annotation types have to be accessible from the annotated type: public, and not nested in a
 * non-public type. The generated classes are placed in the package of the annotated type or package.</p>
 *
 * <p>Kotlin Symbol Processing only exposes the defaults of an annotation type through a usage of it, so a Kotlin
 * annotation type has to be used at least once in the compilation for its builder to know the defaults.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface AnnotationBuilders {

    /**
     * @return The annotation types to generate the builders for
     */
    Class<? extends Annotation>[] value();
}
