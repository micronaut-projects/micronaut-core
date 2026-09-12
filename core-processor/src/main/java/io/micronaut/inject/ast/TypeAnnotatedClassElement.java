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
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A type read through a use of it that carries type annotations of its own: the type is the one delegated to, the
 * type annotations are those of the type and of the use together.
 *
 * <p>{@link ClassElement#getAllTypeArguments()} builds one when it binds the type argument of a super type it
 * reaches through another type, so that what a type variable is bound to and the annotations written where that
 * variable is used are both kept.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.1
 */
@Internal
class TypeAnnotatedClassElement implements ClassElement {

    protected final ClassElement delegate;
    private final ClassElement useSite;

    TypeAnnotatedClassElement(ClassElement delegate, ClassElement useSite) {
        this.delegate = delegate;
        this.useSite = useSite;
    }

    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        return merge(delegate.getTypeAnnotationMetadata(), useSite.getTypeAnnotationMetadata());
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return new AnnotationMetadataHierarchy(true, delegate.getAnnotationMetadata(), useSite.getTypeAnnotationMetadata().getAnnotationMetadata());
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getSimpleName() {
        return delegate.getSimpleName();
    }

    @Override
    public String getPackageName() {
        return delegate.getPackageName();
    }

    @Override
    public PackageElement getPackage() {
        return delegate.getPackage();
    }

    @Override
    public Object getNativeType() {
        return delegate.getNativeType();
    }

    @Override
    public boolean isProtected() {
        return delegate.isProtected();
    }

    @Override
    public boolean isPublic() {
        return delegate.isPublic();
    }

    @Override
    public boolean isPrivate() {
        return delegate.isPrivate();
    }

    @Override
    public boolean isPackagePrivate() {
        return delegate.isPackagePrivate();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }

    @Override
    public boolean isFinal() {
        return delegate.isFinal();
    }

    @Override
    public boolean isAbstract() {
        return delegate.isAbstract();
    }

    @Override
    public boolean isSynthetic() {
        return delegate.isSynthetic();
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return delegate.getModifiers();
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        return delegate.getDocumentation(parseContent);
    }

    @Override
    public boolean isAssignable(String type) {
        return delegate.isAssignable(type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        return delegate.isAssignable(type);
    }

    @Override
    public boolean isAssignable(Class<?> type) {
        return delegate.isAssignable(type);
    }

    @Override
    public boolean isTypeVariable() {
        return delegate.isTypeVariable();
    }

    @Override
    public boolean isGenericPlaceholder() {
        return delegate.isGenericPlaceholder();
    }

    @Override
    public boolean isWildcard() {
        return delegate.isWildcard();
    }

    @Override
    public boolean isRawType() {
        return delegate.isRawType();
    }

    @Override
    public boolean isInterface() {
        return delegate.isInterface();
    }

    @Override
    public boolean isEnum() {
        return delegate.isEnum();
    }

    @Override
    public boolean isRecord() {
        return delegate.isRecord();
    }

    @Override
    public boolean isSealed() {
        return delegate.isSealed();
    }

    @Override
    public boolean isInner() {
        return delegate.isInner();
    }

    @Override
    public boolean isPrimitive() {
        return delegate.isPrimitive();
    }

    @Override
    public boolean isArray() {
        return delegate.isArray();
    }

    @Override
    public int getArrayDimensions() {
        return delegate.getArrayDimensions();
    }

    @Override
    public boolean hasUnresolvedTypes(UnresolvedTypeKind... kind) {
        return delegate.hasUnresolvedTypes(kind);
    }

    @Override
    public Collection<ClassElement> getPermittedSubclasses() {
        return delegate.getPermittedSubclasses();
    }

    @Override
    public Optional<ClassElement> getOptionalValueType() {
        return delegate.getOptionalValueType();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        return delegate.getSuperType();
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        return delegate.getInterfaces();
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        return delegate.getTypeArguments();
    }

    @Override
    public Map<String, ClassElement> getTypeArguments(String type) {
        return delegate.getTypeArguments(type);
    }

    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        return delegate.getAllTypeArguments();
    }

    @Override
    public List<? extends ClassElement> getBoundGenericTypes() {
        return delegate.getBoundGenericTypes();
    }

    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        return delegate.getDeclaredGenericPlaceholders();
    }

    @Override
    public ClassElement getRawClassElement() {
        return delegate.getRawClassElement();
    }

    @Override
    public ClassElement getType() {
        return delegate.getType();
    }

    @Override
    public ClassElement getGenericType() {
        return delegate.getGenericType();
    }

    @Override
    public ClassElement toArray() {
        return withDelegate(delegate.toArray());
    }

    @Override
    public ClassElement fromArray() {
        return withDelegate(delegate.fromArray());
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        return withDelegate(delegate.withTypeArguments(typeArguments));
    }

    @Override
    public ClassElement withTypeArguments(Collection<ClassElement> typeArguments) {
        return withDelegate(delegate.withTypeArguments(typeArguments));
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return withDelegate(delegate.withAnnotationMetadata(annotationMetadata));
    }

    @Override
    @Nullable
    public ClassElement foldBoundGenericTypes(Function<ClassElement, @Nullable ClassElement> fold) {
        ClassElement folded = delegate.foldBoundGenericTypes(fold);
        return folded == null ? null : withDelegate(folded);
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return delegate.getEnclosedElements(query);
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        return delegate.getBeanProperties();
    }

    @Override
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        return delegate.getBeanProperties(propertyElementQuery);
    }

    @Override
    public List<PropertyElement> getSyntheticBeanProperties() {
        return delegate.getSyntheticBeanProperties();
    }

    @Override
    public BeanElementBuilder addAssociatedBean(ClassElement type) {
        return delegate.addAssociatedBean(type);
    }

    /**
     * The annotations of a type and of a use of it, as one.
     *
     * @param type The annotations of the type
     * @param use  The annotations of the use
     * @return The annotations of both, the use first
     */
    static MutableAnnotationMetadataDelegate<AnnotationMetadata> merge(AnnotationMetadataProvider type,
                                                                      AnnotationMetadataProvider use) {
        AnnotationMetadata merged = new AnnotationMetadataHierarchy(true, type.getAnnotationMetadata(), use.getAnnotationMetadata());
        return new MutableAnnotationMetadataDelegate<>() {

            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return merged;
            }

            @Override
            public String toString() {
                return merged.toString();
            }
        };
    }

    /**
     * @param newDelegate The type to read through the same use
     * @return This type read through the same use
     */
    ClassElement withDelegate(ClassElement newDelegate) {
        return new TypeAnnotatedClassElement(newDelegate, useSite);
    }

    /**
     * @return The use the type is read through
     */
    ClassElement getUseSite() {
        return useSite;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
