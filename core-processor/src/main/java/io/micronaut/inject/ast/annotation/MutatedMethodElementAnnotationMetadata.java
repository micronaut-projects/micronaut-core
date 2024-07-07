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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.MethodElement;

/**
 * The mutated element annotation metadata for a method element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class MutatedMethodElementAnnotationMetadata extends AbstractElementAnnotationMetadata {

    private final MethodElement methodElement;
    private final MutableAnnotationMetadataDelegate<?> writeAnnotationMetadata;
    private final AnnotationMetadata readAnnotationMetadata;

    public MutatedMethodElementAnnotationMetadata(@NonNull MethodElement methodElement,
                                                  MutableAnnotationMetadataDelegate<AnnotationMetadata> methodAnnotationMetadata) {
        this.methodElement = methodElement;
        writeAnnotationMetadata = methodAnnotationMetadata;
        readAnnotationMetadata = new AnnotationMetadataHierarchy(
            methodElement.getOwningType(),
            writeAnnotationMetadata
        );
    }

    @Override
    public @NonNull AnnotationMetadata getAnnotationMetadata() {
        return readAnnotationMetadata;
    }

    @Override
    protected @NonNull MethodElement getReturnInstance() {
        return methodElement;
    }

    @Override
    protected @NonNull MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return writeAnnotationMetadata;
    }
}
