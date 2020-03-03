/*
 * Copyright 2017-2020 original authors
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds parameter information for a {@link javax.lang.model.element.ExecutableElement}.
 */
@Internal
class ExecutableElementParamInfo {

    private boolean validated = false;
    private boolean requiresReflection;
    private AnnotationMetadata metadata;
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private Map<String, Object> genericParameters = new LinkedHashMap<>();
    private Map<String, AnnotationMetadata> annotationMetadata = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> genericTypes = new LinkedHashMap<>();

    /**
     * @param requiresReflection Whether reflection is required
     * @param metadata           The annotation metadata
     */
    ExecutableElementParamInfo(boolean requiresReflection, AnnotationMetadata metadata) {
        this.requiresReflection = requiresReflection;
        this.metadata = metadata != null ? metadata : AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Adds a parameter to the info.
     *
     * @param paramName The parameter name
     * @param type      The type reference
     * @param genericType The generic parameter type
     */
    void addParameter(String paramName, Object type, Object genericType) {
        parameters.put(paramName, type);
        genericParameters.put(paramName, genericType);
    }

    /**
     * Adds annotation metadata for the given parameter.
     *
     * @param paramName The parameter name
     * @param metadata  The metadata
     */
    void addAnnotationMetadata(String paramName, AnnotationMetadata metadata) {
        annotationMetadata.put(paramName, metadata);
    }

    /**
     * Adds generics info for the given parameter name.
     *
     * @param paramName The parameter name
     * @param generics  The generics info
     */
    void addGenericTypes(String paramName, Map<String, Object> generics) {
        genericTypes.put(paramName, generics);
    }

    /**
     * @return The parameters
     */
    Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * @return The generic parameters
     */
    Map<String, Object> getGenericParameters() {
        return Collections.unmodifiableMap(genericParameters);
    }

    /**
     * @return The parameter annotation metadata
     */
    Map<String, AnnotationMetadata> getParameterMetadata() {
        return Collections.unmodifiableMap(annotationMetadata);
    }

    /**
     * @return The generic types
     */
    Map<String, Map<String, Object>> getGenericTypes() {
        return Collections.unmodifiableMap(genericTypes);
    }

    /**
     * @return Is reflection required
     */
    boolean isRequiresReflection() {
        return requiresReflection;
    }

    /**
     * @return The annotation metadata for the method
     */
    public AnnotationMetadata getAnnotationMetadata() {
        return metadata;
    }

    /**
     * @return Is the executable validated
     */
    public boolean isValidated() {
        return validated;
    }

    /**
     * @param validated True if it is validated
     */
    public void setValidated(boolean validated) {
        this.validated = validated;
    }
}
