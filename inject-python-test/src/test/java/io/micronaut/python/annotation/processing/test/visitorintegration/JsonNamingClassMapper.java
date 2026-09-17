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
package io.micronaut.python.annotation.processing.test.visitorintegration;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Resolves the naming strategy class of Jackson's {@code JsonNaming} through the visitor context while the
 * annotated class is being built, the way Micronaut Serialization's {@code JsonNamingMapper} does.
 */
public class JsonNamingClassMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "tools.jackson.databind.annotation.JsonNaming";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationClassValue<?> strategy = annotation.annotationClassValue(AnnotationMetadata.VALUE_MEMBER).orElse(null);
        if (strategy == null) {
            return List.of();
        }
        return visitorContext.getClassElement(strategy.getName())
            .map(strategyElement -> List.<AnnotationValue<?>>of(AnnotationValue.builder(NamingStrategy.class)
                .value(nestedSimpleName(strategyElement.getSimpleName()))
                .build()))
            .orElseGet(List::of);
    }

    private static String nestedSimpleName(String simpleName) {
        int nested = simpleName.lastIndexOf('$');
        return nested > -1 ? simpleName.substring(nested + 1) : simpleName;
    }
}
