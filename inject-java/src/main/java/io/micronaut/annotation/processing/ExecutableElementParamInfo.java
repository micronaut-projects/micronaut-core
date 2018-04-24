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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds parameter information for a {@link javax.lang.model.element.ExecutableElement}
 */
@Internal
class ExecutableElementParamInfo {

    boolean requiresReflection = false;
    AnnotationMetadata metadata = AnnotationMetadata.EMPTY_METADATA;
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private Map<String, AnnotationMetadata> annotationMetadata = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> genericTypes = new LinkedHashMap<>();

    void addParameter(String paramName, Object type) {
        parameters.put(paramName, type);
    }

    void addAnnotationMetadata(String paramName, AnnotationMetadata metadata) {
        annotationMetadata.put(paramName, metadata);
    }

    void addGenericTypes(String paramName, Map<String, Object> generics) {
        genericTypes.put(paramName, generics);
    }

    Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    Map<String, AnnotationMetadata> getParameterMetadata() {
        return Collections.unmodifiableMap(annotationMetadata);
    }

    Map<String, Map<String, Object>> getGenericTypes() {
        return Collections.unmodifiableMap(genericTypes);
    }

    boolean isRequiresReflection() {
        return requiresReflection;
    }

    public AnnotationMetadata getAnnotationMetadata() {
        return metadata;
    }
}
