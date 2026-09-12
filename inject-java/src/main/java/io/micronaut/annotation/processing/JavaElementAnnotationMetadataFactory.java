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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.AbstractJavaElement;
import io.micronaut.annotation.processing.visitor.JavaGenericPlaceholderElement;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import java.util.List;
import java.util.function.Supplier;

/**
 * Java element annotation metadata factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class JavaElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<Element, AnnotationMirror> {

    private static final ElementAnnotationMetadata EMPTY = new ElementAnnotationMetadata() {
    };

    public JavaElementAnnotationMetadataFactory(boolean isReadOnly, JavaAnnotationMetadataBuilder metadataBuilder) {
        super(isReadOnly, metadataBuilder);
    }

    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new JavaElementAnnotationMetadataFactory(true, (JavaAnnotationMetadataBuilder) metadataBuilder);
    }

    @Override
    public ElementAnnotationMetadata build(io.micronaut.inject.ast.Element element) {
        AbstractJavaElement javaElement = (AbstractJavaElement) element;
        if (!allowedAnnotations(javaElement)) {
            return EMPTY;
        }
        return super.build(element);
    }

    private static boolean allowedAnnotations(AbstractJavaElement javaElement) {
        return javaElement.getNativeType().element() != null;
    }

    @Override
    protected Element getNativeElement(io.micronaut.inject.ast.Element element) {
        return ((AbstractJavaElement) element).getNativeType().element();
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForClass(ClassElement classElement) {
        var clazz = (JavaNativeElement.Class) classElement.getNativeType();
        TypeMirror typeMirror = clazz.typeMirror();
        if (typeMirror == null) {
            return super.lookupTypeAnnotationsForClass(classElement);
        }
        TypeMirror writtenMirror = typeMirror;
        if (classElement.getArrayDimensions() > 0 && !(typeMirror instanceof ArrayType)) {
            // An array made with toArray() from a component: the metadata reads the component's annotations,
            // as before, but no source wrote this dimension
            return new SourceAnnotationsCachedAnnotationMetadata(metadataBuilder.lookupOrBuild(clazz, new AnnotationsElement(typeMirror)), List::of);
        }
        // Every dimension of an array holds its own mirror; the metadata keeps reading the innermost one,
        // as it did when all the dimensions shared it
        while (typeMirror instanceof ArrayType arrayType && arrayType.getComponentType() instanceof ArrayType) {
            typeMirror = arrayType.getComponentType();
        }
        if (typeMirror instanceof ArrayType arrayType) {
            if (!hasJSpecifyAnnotations(arrayType) && !hasJSpecifyAnnotations(arrayType.getComponentType())) {
                // Backward compatibility for Micronaut type annotations support
                typeMirror = arrayType.getComponentType();
            }
        }
        AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata cached = metadataBuilder.lookupOrBuild(clazz, new AnnotationsElement(typeMirror));
        if (typeMirror == writtenMirror) {
            return cached;
        }
        // The source view is exact per dimension
        return new SourceAnnotationsCachedAnnotationMetadata(cached, () -> metadataBuilder.readSourceAnnotations(new AnnotationsElement(writtenMirror)));
    }

    private boolean hasJSpecifyAnnotations(TypeMirror element) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().startsWith("org.jspecify.annotations")) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForGenericPlaceholder(GenericPlaceholderElement placeholderElement) {
        var genericNativeType = (JavaNativeElement.Placeholder) placeholderElement.getGenericNativeType();
        Element placeholderJavaElement;
        TypeVariable placeholderTypeVariable = genericNativeType.typeVariable();
        if (!placeholderTypeVariable.getAnnotationMirrors().isEmpty()) {
            placeholderJavaElement = new AnnotationsElement(placeholderTypeVariable);
        } else {
            placeholderJavaElement = genericNativeType.element();
        }
        AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata cached = metadataBuilder.lookupOrBuild(genericNativeType, placeholderJavaElement);
        // The metadata falls back to the type parameter declaration when a use of the variable wrote nothing;
        // the source view does not: a use reports what was written at the use, a declaration reports the
        // annotations of the type parameter
        boolean declaration = placeholderElement instanceof JavaGenericPlaceholderElement javaPlaceholder && javaPlaceholder.isDeclaration();
        Element sourceElement = declaration ? genericNativeType.element() : new AnnotationsElement(placeholderTypeVariable);
        return new SourceAnnotationsCachedAnnotationMetadata(cached, () -> metadataBuilder.readSourceAnnotations(sourceElement));
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForWildcard(WildcardElement wildcardElement) {
        var wildcard = (WildcardType) wildcardElement.getGenericNativeType();
        return metadataBuilder.lookupOrBuild(wildcard, new AnnotationsElement(wildcard));
    }

    /**
     * A cache entry whose source annotations are read from a different element than the metadata was built
     * from: the metadata of a type use is shared, the source view is exact.
     */
    private static final class SourceAnnotationsCachedAnnotationMetadata implements AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata {

        private final AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata delegate;
        @Nullable
        private Supplier<List<AnnotationValue<?>>> sourceAnnotationsSupplier;
        @Nullable
        private List<AnnotationValue<?>> sourceAnnotations;

        SourceAnnotationsCachedAnnotationMetadata(AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata delegate,
                                                  Supplier<List<AnnotationValue<?>>> sourceAnnotationsSupplier) {
            this.delegate = delegate;
            this.sourceAnnotationsSupplier = sourceAnnotationsSupplier;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return delegate.getAnnotationMetadata();
        }

        @Override
        public boolean isMutated() {
            return delegate.isMutated();
        }

        @Override
        public void update(AnnotationMetadata annotationMetadata) {
            delegate.update(annotationMetadata);
        }

        @Override
        public boolean wasCleared() {
            return delegate.wasCleared();
        }

        @Override
        public void markCleared() {
            delegate.markCleared();
        }

        @Override
        public List<AnnotationValue<?>> getSourceAnnotations() {
            if (sourceAnnotations == null) {
                sourceAnnotations = sourceAnnotationsSupplier.get();
                sourceAnnotationsSupplier = null;
            }
            return sourceAnnotations;
        }
    }

}
