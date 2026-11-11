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
package io.micronaut.aop.internal;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Resolves the interceptor bindings of an element.
 *
 * @since 5.3.0
 */
@Internal
public final class InterceptorBindings {

    private InterceptorBindings() {
    }

    /**
     * Resolve interceptor binding for the given annotation metadata and kind.
     *
     * @param annotationMetadata The annotation metadata
     * @param kind The kind
     * @return The binding
     */
    public static Collection<AnnotationValue<?>> resolveInterceptorValues(AnnotationMetadata annotationMetadata, InterceptorKind kind) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            final List<AnnotationValue<InterceptorBinding>> declaredValues =
                annotationMetadataHierarchy.getDeclaredMetadata().getAnnotationValuesByType(InterceptorBinding.class);
            final List<AnnotationValue<InterceptorBinding>> parentValues =
                annotationMetadataHierarchy.getRootMetadata()
                    .getAnnotationValuesByType(InterceptorBinding.class);
            if (CollectionUtils.isEmpty(declaredValues) && CollectionUtils.isEmpty(parentValues)) {
                return Collections.emptyList();
            }
            Set<AnnotationValue<?>> resolved = CollectionUtils.newHashSet(declaredValues.size() + parentValues.size());
            Set<String> declared = CollectionUtils.newHashSet(declaredValues.size());
            for (AnnotationValue<InterceptorBinding> declaredValue : declaredValues) {
                final String annotationName = declaredValue.stringValue().orElse(null);
                if (annotationName != null) {
                    final InterceptorKind specifiedKind = declaredValue.enumValue("kind", InterceptorKind.class).orElse(null);
                    if (specifiedKind == null || specifiedKind.equals(kind)) {
                        if (!annotationMetadata.isRepeatableAnnotation(annotationName)) {
                            declared.add(annotationName);
                        }
                        resolved.add(declaredValue);
                    }
                }
            }
            for (AnnotationValue<InterceptorBinding> parentValue : parentValues) {
                final String annotationName = parentValue.stringValue().orElse(null);
                if (annotationName != null && !declared.contains(annotationName)) {
                    final InterceptorKind specifiedKind = parentValue.enumValue("kind", InterceptorKind.class).orElse(null);
                    if (specifiedKind == null || specifiedKind.equals(kind)) {
                        resolved.add(parentValue);
                    }
                }
            }

            return resolved;
        } else {
            List<AnnotationValue<InterceptorBinding>> bindings = annotationMetadata
                .getAnnotationValuesByType(InterceptorBinding.class);
            if (CollectionUtils.isEmpty(bindings)) {
                return Collections.emptyList();
            }
            Set<AnnotationValue<?>> selectedBindings = CollectionUtils.newHashSet(bindings.size());
            for (AnnotationValue<InterceptorBinding> av : bindings) {
                if (av.stringValue().isEmpty()) {
                    continue;
                }
                final InterceptorKind specifiedKind = av.enumValue("kind", InterceptorKind.class).orElse(null);
                if (specifiedKind == null || specifiedKind.equals(kind)) {
                    selectedBindings.add(av);
                }
            }
            return selectedBindings;
        }
    }
}
