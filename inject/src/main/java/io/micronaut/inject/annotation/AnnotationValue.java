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

import io.micronaut.core.convert.value.ConvertibleValues;

import java.util.Collections;
import java.util.Map;

/**
 * A type for representation annotation values in order to support {@link java.lang.annotation.Repeatable} annotations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationValue {

    private final String annotationName;
    private final ConvertibleValues<Object> convertibleValues;

    public AnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = ConvertibleValues.of(values);
    }

    @SuppressWarnings("unchecked")
    public AnnotationValue(String annotationName) {
        this.annotationName = annotationName;
        this.convertibleValues = ConvertibleValues.EMPTY;
    }

    public AnnotationValue(String annotationName, ConvertibleValues<Object> convertibleValues) {
        this.annotationName = annotationName;
        this.convertibleValues = convertibleValues;
    }

    /**
     * @return The annotation name
     */
    public String getAnnotationName() {
        return annotationName;
    }

    /**
     * @return The attribute values
     */
    @SuppressWarnings("unchecked")
    public Map<CharSequence, Object> getValues() {
        return (Map)convertibleValues.asMap();
    }

    /**
     * @return The attribute values
     */
    public ConvertibleValues<Object> getConvertibleValues() {
        return convertibleValues;
    }
}
