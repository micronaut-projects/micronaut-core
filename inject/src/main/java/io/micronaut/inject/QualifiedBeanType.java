/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * An interface for a {@link BeanType} that allows qualifiers.
 *
 * @param <T> The bean type
 * @since 4.0.0
 */
public interface QualifiedBeanType<T> extends BeanType<T>, AnnotationMetadataDelegate {

    /**
     * Resolve the declared qualifier for this bean.
     * @return The qualifier or null if this isn't one
     */
    @SuppressWarnings("java:S3776")
    default @Nullable Qualifier<T> getDeclaredQualifier() {
        AnnotationMetadata annotationMetadata = getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            // Beans created by a factory will have AnnotationMetadataHierarchy = producing element + factory class
            // All qualifiers are removed from the factory class anyway, so we can skip the hierarchy
            annotationMetadata = annotationMetadata.getDeclaredMetadata();
        }
        List<AnnotationValue<Annotation>> annotations = AnnotationUtil.findQualifierAnnotations(annotationMetadata);
        if (!annotations.isEmpty()) {
            if (annotations.size() == 1) {
                final AnnotationValue<Annotation> annotationValue = annotations.iterator().next();
                if (annotationValue.getAnnotationName().equals(Qualifier.PRIMARY)) {
                    // primary is the same as null
                    return null;
                }
                return (Qualifier<T>) Qualifiers.byAnnotation(annotationMetadata, annotationValue);
            } else {
                Qualifier<T>[] qualifiers = new Qualifier[annotations.size()];
                int i = 0;
                for (AnnotationValue<Annotation> annotationValue : annotations) {
                    qualifiers[i++] = (Qualifier<T>) Qualifiers.byAnnotation(annotationMetadata, annotationValue);
                }
                return Qualifiers.byQualifiers(qualifiers);
            }
        } else {
            Qualifier<T> qualifier = resolveDynamicQualifier();
            if (qualifier == null) {
                String name = annotationMetadata.stringValue(AnnotationUtil.NAMED).orElse(null);
                qualifier = name != null ? Qualifiers.byAnnotation(annotationMetadata, name) : null;
            }
            return qualifier;
        }
    }

    /**
     * @return Method that can be overridden to resolve a dynamic qualifier
     * @deprecated No longer used.
     */
    @Deprecated(since = "5.0", forRemoval = true)
    default @Nullable Qualifier<T> resolveDynamicQualifier() {
        return null;
    }
}
