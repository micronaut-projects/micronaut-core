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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

import java.util.List;
import java.util.Optional;

/**
 * A type variable read through a use of it that carries type annotations of its own: the variable is the one
 * delegated to, the type annotations are those of the use.
 *
 * @author Denis Stepanov
 * @since 5.2.1
 */
@Internal
@Experimental
public final class TypeAnnotatedGenericPlaceholderElement extends TypeAnnotatedClassElement implements GenericPlaceholderElement {

    /**
     * @param delegate               The type variable
     * @param typeAnnotationMetadata The type annotations to read it with
     */
    public TypeAnnotatedGenericPlaceholderElement(GenericPlaceholderElement delegate,
                                                  MutableAnnotationMetadataDelegate<AnnotationMetadata> typeAnnotationMetadata) {
        super(delegate, typeAnnotationMetadata);
    }

    @Override
    public List<? extends ClassElement> getBounds() {
        return placeholder().getBounds();
    }

    @Override
    public String getVariableName() {
        return placeholder().getVariableName();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return placeholder().getDeclaringElement();
    }

    @Override
    public Optional<ClassElement> getResolved() {
        // The type the variable resolves to is read the same way, so the annotations survive the resolution
        return placeholder().getResolved().map(this::withDelegate);
    }

    @Override
    public Object getGenericNativeType() {
        return placeholder().getGenericNativeType();
    }

    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getGenericTypeAnnotationMetadata() {
        return typeAnnotationMetadata;
    }

    @Override
    protected ClassElement withDelegate(ClassElement newDelegate) {
        if (newDelegate instanceof GenericPlaceholderElement newPlaceholder) {
            return new TypeAnnotatedGenericPlaceholderElement(newPlaceholder, typeAnnotationMetadata);
        }
        return super.withDelegate(newDelegate);
    }

    private GenericPlaceholderElement placeholder() {
        return (GenericPlaceholderElement) delegate;
    }
}
