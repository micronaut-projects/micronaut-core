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
package io.micronaut.inject.annotation;

import java.lang.annotation.Annotation;

/**
 * A typed {@link AnnotationMapper} operates against a concrete annotation type. Mapper implementations
 * that implement this class require the annotations to exist on the annotation processor classpath. If this
 * is problematic consider {@link NamedAnnotationMapper}.
 *
 * @param <T> The annotation type.
 */
public interface TypedAnnotationMapper<T extends Annotation> extends AnnotationMapper<T> {

    /**
     * The annotation type to be mapped.
     *
     * @return The annotation type
     */
    Class<T> annotationType();
}
