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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the {@link ParameterElement} interface for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class JavaParameterElement extends AbstractTypeAwareJavaElement implements ParameterElement, TypedElement {

    private final JavaClassElement owningType;
    private final JavaMethodElement methodElement;
    private ClassElement typeElement;
    private ClassElement genericTypeElement;

    /**
     * Default constructor.
     *
     * @param owningType The owning class
     * @param methodElement The method element
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     */
    JavaParameterElement(JavaClassElement owningType,
                         JavaMethodElement methodElement,
                         JavaNativeElement.Variable nativeElement,
                         ElementAnnotationMetadataFactory annotationMetadataFactory,
                         JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
        this.owningType = owningType;
        this.methodElement = methodElement;
    }

    @NonNull
    @Override
    public JavaNativeElement.Variable getNativeType() {
        return (JavaNativeElement.Variable) super.getNativeType();
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaParameterElement(owningType, methodElement, getNativeType(), elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ParameterElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    protected boolean hasNullMarked() {
        return methodElement.hasNullMarked();
    }

    @Override
    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    @Override
    public boolean isArray() {
        return getType().isArray();
    }

    @Override
    public int getArrayDimensions() {
        return getType().getArrayDimensions();
    }

    @Override
    @NonNull
    public ClassElement getType() {
        if (typeElement == null) {
            typeElement = newClassElement(getNativeType(), getNativeType().element().asType(), Collections.emptyMap());
        }
        return typeElement;
    }

    @NonNull
    @Override
    public ClassElement getGenericType() {
        if (genericTypeElement == null) {
            genericTypeElement = newClassElement(getNativeType(), getNativeType().element().asType(), methodElement.getTypeArguments());
        }
        return genericTypeElement;
    }

    @Override
    public MethodElement getMethodElement() {
        return methodElement;
    }

    @Override
    protected AnnotationMetadata getTypeAnnotationMetadata() {
        return getType().getTypeAnnotationMetadata();
    }

    @Override
    public Optional<String> getDocumentation() {
        try {
            String methodDocComment = visitorContext.getElements().getDocComment(methodElement.getNativeType().element());
            if (methodDocComment != null) {
                String parameterDoc = findParameterDoc(methodDocComment, getName());
                if (parameterDoc != null) {
                    return Optional.of(parameterDoc);
                }
            }
            if (owningType.isRecord() && methodElement instanceof ConstructorElement constructor) {
                final List<PropertyElement> beanProperties = constructor
                    .getDeclaringType()
                    .getBeanProperties();
                final ParameterElement[] parameters = constructor.getParameters();
                if (beanProperties.size() == parameters.length) {
                    String docComment = visitorContext.getElements().getDocComment(owningType.getNativeType().element());
                    if (docComment != null) {
                        return Optional.ofNullable(findParameterDoc(docComment, getName()));
                    }
                }
            }
            return Optional.empty();
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    @Nullable
    private static String findParameterDoc(String javadocString, String name) {
        try {
            Javadoc javadoc = StaticJavaParser.parseJavadoc(javadocString);
            if (javadoc == null) {
                return null;
            }
            for (JavadocBlockTag t : javadoc.getBlockTags()) {
                if (t.getType() == JavadocBlockTag.Type.PARAM && t.getName().map(n -> n.equals(name)).orElse(false)) {
                    return t.getContent().toText();
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

}
