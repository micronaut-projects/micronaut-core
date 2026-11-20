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
package io.micronaut.python.annotation.processing.test.retain;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Retainable;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;

/**
 * Marks the contract and the marker retainable, and sets the contract, the marker and the annotations carrying the
 * contract as the stereotypes of each annotation held in a container, before the metadata builder sees it, as the
 * validation integration does for constraints.
 */
public final class ContainerContractRemapper implements AnnotationRemapper {

    private static final String CONTRACT = Contract.class.getName();
    private static final String SINGLE = Single.class.getName();

    @Override
    public String getPackageName() {
        return "io.micronaut.python.annotation.processing.test.retain";
    }

    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        if (annotation.getAnnotationName().equals(CONTRACT) || annotation.getAnnotationName().equals(SINGLE)) {
            return List.of(annotation.mutate().stereotype(AnnotationValue.builder(Retainable.class).build()).build());
        }
        List<AnnotationValue<Annotation>> contained = annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        if (contained.isEmpty()) {
            return List.of(annotation);
        }
        AnnotationValue<?>[] rewritten = new AnnotationValue<?>[contained.size()];
        for (int i = 0; i < rewritten.length; i++) {
            AnnotationValue<?> value = contained.get(i);
            Optional<ClassElement> type = visitorContext.getClassElement(value.getAnnotationName());
            AnnotationValue<?> contract = type.map(t -> t.getAnnotationMetadata().getAnnotation(CONTRACT)).orElse(null);
            if (contract == null) {
                rewritten[i] = value;
                continue;
            }
            ClassElement element = type.get();
            AnnotationValueBuilder<?> builder = value.mutate().stereotype(contract);
            element.getAnnotationMetadata().findAnnotation(SINGLE).ifPresent(builder::stereotype);
            for (String name : element.getAnnotationNamesByStereotype(CONTRACT)) {
                element.getAnnotationValuesByName(name).forEach(builder::stereotype);
            }
            rewritten[i] = builder.build();
        }
        return List.of(annotation.mutate().member(AnnotationMetadata.VALUE_MEMBER, rewritten).build());
    }
}
