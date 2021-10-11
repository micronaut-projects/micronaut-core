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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Holds parameter information for a {@link javax.lang.model.element.ExecutableElement}.
 */
@Internal
class ExecutableElementParamInfo {

    private boolean validated = false;
    private final boolean requiresReflection;
    private final AnnotationMetadata metadata;
    private final Map<String, ParameterElement> parameters = new LinkedHashMap<>(10);
    private final Map<String, ClassElement> genericParameters = new LinkedHashMap<>(10);
    private final Map<String, ClassElement> parameterTypes = new LinkedHashMap<>(10);

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
     * @param paramName    The parameter name
     * @param classElement The class element
     */
    void addParameter(String paramName, ParameterElement classElement) {
        parameters.put(paramName, classElement);
        parameterTypes.put(paramName, classElement.getType());
        genericParameters.put(paramName, classElement.getGenericType());
    }

    /**
     * @return The parameters
     */
    Map<String, ParameterElement> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * @return The parameter types
     */
    Map<String, ClassElement> getParameterTypes() {
        return Collections.unmodifiableMap(parameterTypes);
    }

    /**
     * @return The generic parameters
     */
    Map<String, ClassElement> getGenericParameterTypes() {
        return Collections.unmodifiableMap(genericParameters);
    }

    /**
     * @return The parameter annotation metadata
     */
    Map<String, AnnotationMetadata> getParameterMetadata() {
        return getParameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry -> entry.getValue().getAnnotationMetadata())));
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
