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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A type for representation annotation values in order to support {@link java.lang.annotation.Repeatable} annotations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class AnnotationValue {

    private final String annotationName;
    private final ConvertibleValues<Object> convertibleValues;
    private final Map<CharSequence, Object> values;

    /**
     * @param annotationName The annotation name
     * @param values         The values
     */
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = ConvertibleValues.of(values);
        this.values = values;
    }

    /**
     * @param annotationName The annotation name
     */
    @SuppressWarnings("unchecked")
    public AnnotationValue(String annotationName) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = ConvertibleValues.EMPTY;
        this.values = Collections.EMPTY_MAP;
    }

    /**
     * @param annotationName    The annotation name
     * @param convertibleValues The convertible values
     */
    public AnnotationValue(String annotationName, ConvertibleValues<Object> convertibleValues) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = convertibleValues;
        Map<String, Object> existing = convertibleValues.asMap();
        this.values = new LinkedHashMap<>(existing.size());
        this.values.putAll(existing);
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
        return (Map) convertibleValues.asMap();
    }

    /**
     * @return The attribute values
     */
    public ConvertibleValues<Object> getConvertibleValues() {
        return convertibleValues;
    }

    @Override
    public int hashCode() {
        return AnnotationMetadataSupport.calculateHashCode(values);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!AnnotationValue.class.isInstance(obj)) {
            return false;
        }

        AnnotationValue other = AnnotationValue.class.cast(obj);

        Map<CharSequence, Object> otherValues = other.values;

        if (values.size() != otherValues.size()) {
            return false;
        }

        // compare annotation member values
        for (Map.Entry<CharSequence, Object> member : values.entrySet()) {
            Object value = member.getValue();
            Object otherValue = otherValues.get(member.getKey());

            if (!AnnotationMetadataSupport.areEqual(value, otherValue)) {
                return false;
            }
        }

        return true;
    }
}
