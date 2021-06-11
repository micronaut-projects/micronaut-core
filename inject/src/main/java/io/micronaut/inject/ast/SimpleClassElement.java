/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

@Internal
final class SimpleClassElement implements ClassElement {
    private final String typeName;
    private final boolean isInterface;
    private final AnnotationMetadata annotationMetadata;
    private final Map<String, ClassElement> typeArguments;

    SimpleClassElement(String typeName) {
        this(typeName, false, AnnotationMetadata.EMPTY_METADATA);
    }

    SimpleClassElement(String typeName, boolean isInterface, AnnotationMetadata annotationMetadata) {
        this(typeName, isInterface, annotationMetadata, Collections.emptyMap());
    }

    SimpleClassElement(String typeName, boolean isInterface, AnnotationMetadata annotationMetadata, Map<String, ClassElement> typeArguments) {
        this.typeName = typeName;
        this.isInterface = isInterface;
        this.annotationMetadata = annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA;
        this.typeArguments = typeArguments;
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArguments() {
        return this.typeArguments;
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArguments(@NonNull String type) {
        if (this.typeName.equals(type)) {
            return this.typeArguments;
        } else {
            return ClassElement.super.getTypeArguments(type);
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isAssignable(String type) {
        return typeName.equals(type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        return false;
    }

    @Override
    public ClassElement toArray() {
        throw new UnsupportedOperationException("Cannot convert class elements produced by name to an array");
    }

    @Override
    public ClassElement fromArray() {
        throw new UnsupportedOperationException("Cannot convert class elements produced by from an array");
    }

    @NotNull
    @Override
    public String getName() {
        return typeName;
    }

    @Override
    public boolean isPackagePrivate() {
        return false;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return false;
    }

    @NotNull
    @Override
    public Object getNativeType() {
        return typeName;
    }
}
