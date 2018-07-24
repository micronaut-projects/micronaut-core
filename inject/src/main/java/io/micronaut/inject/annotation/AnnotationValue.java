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

package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;

import java.util.Map;

/**
 * A type for representation annotation values in order to support {@link java.lang.annotation.Repeatable} annotations.
 *
 * @deprecated Replaced with {@link io.micronaut.core.annotation.AnnotationValue}
 * @author Graeme Rocher
 * @since 1.0
 */
@Deprecated
@Internal
public final class AnnotationValue extends io.micronaut.core.annotation.AnnotationValue {

    /**
     * Constructor.
     *
     * @param annotationName The annotation name
     * @param values The values
     */
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        super(annotationName, values);
    }

    /**
     * Constructor.
     *
     * @param annotationName The annotation name
     */
    public AnnotationValue(String annotationName) {
        super(annotationName);
    }

    /**
     * Constructor.
     *
     * @param annotationName The annotation name
     * @param convertibleValues The convertible values
     */
    public AnnotationValue(String annotationName, ConvertibleValues<Object> convertibleValues) {
        super(annotationName, convertibleValues);
    }
}
