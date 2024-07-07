/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.annotation.internal;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Qualifier;

import java.util.List;
import java.util.Optional;

/**
 * The remapped adds a non-binding attribute to any qualifiers that are stereotypes.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class QualifierBindingMembers implements AnnotationRemapper {

    @NonNull
    @Override
    public String getPackageName() {
        return ALL_PACKAGES;
    }

    @NonNull
    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        if (annotation.getStereotypes() != null) {
            String[] nonBindingMembers = annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE);
            if (nonBindingMembers.length > 0) {
                Optional<AnnotationValue<?>> qualifier = annotation.getStereotypes()
                    .stream()
                    .filter(av -> av.getAnnotationName().equals(AnnotationUtil.QUALIFIER) || av.getAnnotationName().equals(Qualifier.class.getName()))
                    .findFirst();
                if (qualifier.isPresent()) {
                    AnnotationValue<?> originalQualifier = qualifier.get();
                    AnnotationValue<?> newQualifier = originalQualifier.mutate()
                        .member(AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBindingMembers).build();
                    annotation = annotation.mutate().replaceStereotype(originalQualifier, newQualifier).build();
                }
            }
        }
        return List.of(annotation);
    }
}
