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
package io.micronaut.annotation.processing.visitor;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

/**
 * Represents the void type.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated Use {@link PrimitiveElement#VOID} instead.
 */
@Internal
@Deprecated
final class JavaVoidElement implements ClassElement, AnnotationMetadataDelegate {

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isAssignable(String type) {
        return "void".equals(type);
    }

    @Override
    public ClassElement toArray() {
        return PrimitiveElement.VOID.toArray();
    }

    @Override
    public ClassElement fromArray() {
        return PrimitiveElement.VOID.fromArray();
    }

    @Override
    public String getName() {
        return "void";
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    @NonNull
    public Object getNativeType() {
        return void.class;
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

}
