/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.UnresolvedTypeKind;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.beans.BeanElementBuilder;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Internal
public record TypeAnnotatedClassElement(
    ClassElement delegate,
    ElementAnnotationMetadata typeAnnotationMetadata
) implements ClassElement {

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return new AnnotationMetadataHierarchy(true, delegate.getAnnotationMetadata(), typeAnnotationMetadata);
    }

    @Override
    public MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        return typeAnnotationMetadata;
    }

    @Override
    public <T extends Annotation> ClassElement annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        typeAnnotationMetadata.annotate(annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> ClassElement annotate(AnnotationValue<T> annotationValue) {
        typeAnnotationMetadata.annotate(annotationValue);
        return this;
    }

    @Override
    public ClassElement removeAnnotation(String annotationType) {
        typeAnnotationMetadata.removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> ClassElement removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        typeAnnotationMetadata.removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public ClassElement removeStereotype(String annotationType) {
        typeAnnotationMetadata.removeStereotype(annotationType);
        return this;
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
    public boolean hasUnresolvedTypes(UnresolvedTypeKind... kind) {
        return delegate.hasUnresolvedTypes(kind);
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
    public boolean isOptional() {
        return delegate.isOptional();
    }

    @Override
    public Optional<ClassElement> getOptionalValueType() {
        return delegate.getOptionalValueType();
    }

    @Override
    public boolean isContainerType() {
        return delegate.isContainerType();
    }

    @Override
    public boolean isRecord() {
        return delegate.isRecord();
    }

    @Override
    public boolean isInner() {
        return delegate.isInner();
    }

    @Override
    public boolean isEnum() {
        return delegate.isEnum();
    }

    @Override
    public ClassElement toArray() {
        return new TypeAnnotatedClassElement(delegate.toArray(), typeAnnotationMetadata);
    }

    @Override
    public ClassElement fromArray() {
        return new TypeAnnotatedClassElement(delegate.fromArray(), typeAnnotationMetadata);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isPackagePrivate() {
        return delegate.isPackagePrivate();
    }

    @Override
    public boolean isSynthetic() {
        return delegate.isSynthetic();
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
    public Set<ElementModifier> getModifiers() {
        return delegate.getModifiers();
    }

    @Override
    public boolean isAbstract() {
        return delegate.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        return delegate.getDocumentation(parseContent);
    }

    @Override
    public boolean isPrivate() {
        return delegate.isPrivate();
    }

    @Override
    public boolean isFinal() {
        return delegate.isFinal();
    }

    @Override
    public String getDescription(boolean simple) {
        return delegate.getDescription(simple);
    }

    @Override
    public Object getNativeType() {
        return delegate.getNativeType();
    }

    @Override
    public boolean isPrimitive() {
        return delegate.isPrimitive();
    }

    @Override
    public boolean isVoid() {
        return delegate.isVoid();
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
    public boolean isInterface() {
        return delegate.isInterface();
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
    public PackageElement getPackage() {
        return delegate.getPackage();
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        return delegate.getBeanProperties();
    }

    @Override
    public List<PropertyElement> getSyntheticBeanProperties() {
        return delegate.getSyntheticBeanProperties();
    }

    @Override
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        return delegate.getBeanProperties(propertyElementQuery);
    }

    @Override
    public List<FieldElement> getFields() {
        return delegate.getFields();
    }

    @Override
    public List<MethodElement> getMethods() {
        return delegate.getMethods();
    }

    @Override
    public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return delegate.getEnclosedElements(query);
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        return delegate.getEnclosingType();
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
    public Map<String, ClassElement> getTypeArguments(String type) {
        return delegate.getTypeArguments(type);
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        return delegate.getTypeArguments();
    }

    @Override
    public ClassElement getRawClassElement() {
        return new TypeAnnotatedClassElement(delegate.getRawClassElement(), typeAnnotationMetadata);
    }

    @Override
    public BeanElementBuilder addAssociatedBean(ClassElement type) {
        return delegate.addAssociatedBean(type);
    }

    @Override
    public List<ConstructorElement> getAccessibleConstructors() {
        return delegate.getAccessibleConstructors();
    }

    @Override
    public List<MethodElement> getAccessibleStaticCreators() {
        return delegate.getAccessibleStaticCreators();
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return new TypeAnnotatedClassElement(
            delegate.withAnnotationMetadata(annotationMetadata),
            typeAnnotationMetadata
        );
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        return new TypeAnnotatedClassElement(
            delegate.withTypeArguments(typeArguments),
            typeAnnotationMetadata
        );
    }

    @Override
    public ClassElement withTypeArguments(Collection<ClassElement> typeArguments) {
        return new TypeAnnotatedClassElement(
            delegate.withTypeArguments(typeArguments),
            typeAnnotationMetadata
        );
    }
}
