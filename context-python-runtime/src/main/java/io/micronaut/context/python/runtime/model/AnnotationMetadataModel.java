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
package io.micronaut.context.python.runtime.model;

import java.util.List;
import java.util.Map;

/**
 * Resolved annotation metadata: the same five maps the build-time writer emits into a
 * {@code DefaultAnnotationMetadata} constructor, plus the annotation defaults and repeatable containers it registers.
 * Source-retained annotations are already removed; the compile-time effects they had are part of the other members.
 *
 * @param declaredAnnotations     The declared annotations and their values
 * @param declaredStereotypes     The declared stereotypes and their values
 * @param allStereotypes          All stereotypes and their values
 * @param allAnnotations          All annotations and their values
 * @param annotationsByStereotype The annotation names by stereotype
 * @param annotationDefaults      The default values of the annotations that have any
 * @param repeatableContainers    The repeatable annotation names by their container
 * @param hasPropertyExpressions  Whether any value contains a property placeholder
 * @since 5.3.0
 */
public record AnnotationMetadataModel(
    Map<String, Map<String, Object>> declaredAnnotations,
    Map<String, Map<String, Object>> declaredStereotypes,
    Map<String, Map<String, Object>> allStereotypes,
    Map<String, Map<String, Object>> allAnnotations,
    Map<String, List<String>> annotationsByStereotype,
    Map<String, Map<String, Object>> annotationDefaults,
    Map<String, String> repeatableContainers,
    boolean hasPropertyExpressions) {

    /**
     * Empty metadata.
     */
    public static final AnnotationMetadataModel EMPTY = new AnnotationMetadataModel(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), false);

    /**
     * @return Whether no annotation is recorded
     */
    public boolean isEmpty() {
        return declaredAnnotations.isEmpty() && declaredStereotypes.isEmpty() && allStereotypes.isEmpty()
            && allAnnotations.isEmpty() && annotationsByStereotype.isEmpty();
    }
}
