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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;

/**
 * A reference to {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationMetadataReference implements AnnotationMetadataDelegate {

    private final String className;
    private final AnnotationMetadata annotationMetadata;

    /**
     * @param className          The class name
     * @param annotationMetadata The annotation metadata
     */
    public AnnotationMetadataReference(String className, AnnotationMetadata annotationMetadata) {
        this.className = className;
        this.annotationMetadata = annotationMetadata;
    }

    /**
     * @return The target metadata
     */
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    /**
     * @return The class name of the annotation metadata
     */
    public String getClassName() {
        return className;
    }

    @Override
    public AnnotationMetadata unwrap() {
        // Don't unwrap the reference
        return this;
    }
}
