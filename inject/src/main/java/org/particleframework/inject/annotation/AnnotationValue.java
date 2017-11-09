/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.annotation;

import java.util.Map;

/**
 * A type for representation annotation values in order to support {@link java.lang.annotation.Repeatable} annotations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationValue {
    private final String annotationName;
    private final Map<CharSequence,Object> values;

    public AnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        this.annotationName = annotationName.intern();
        this.values = values;
    }

    public AnnotationValue(String annotationName) {
        this.annotationName = annotationName;
        this.values = null;
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
    public Map<CharSequence, Object> getValues() {
        return values;
    }
}
