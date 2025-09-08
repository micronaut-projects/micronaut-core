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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.Map;

/**
 * A field element returning data from a {@link VariableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
class JavaFieldElement extends AbstractJavaMemberElement implements FieldElement {

    private final VariableElement variableElement;
    private JavaClassElement owningType;
    private ClassElement type;
    private ClassElement genericType;
    private ClassElement resolvedDeclaringClass;

    /**
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     */
    JavaFieldElement(JavaNativeElement.Variable nativeElement,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
        this.variableElement = nativeElement.element();
    }

    /**
     * @param owningType The declaring element
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     */
    JavaFieldElement(JavaClassElement owningType,
                     JavaNativeElement.Variable nativeElement,
                     ElementAnnotationMetadataFactory annotationMetadataFactory,
                     JavaVisitorContext visitorContext) {
        this(nativeElement, annotationMetadataFactory, visitorContext);
        this.owningType = owningType;
    }

    @Override
    protected AnnotationMetadata getTypeAnnotationMetadata() {
        return getType().getTypeAnnotationMetadata();
    }

    @NonNull
    @Override
    public JavaNativeElement.Variable getNativeType() {
        return (JavaNativeElement.Variable) super.getNativeType();
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaFieldElement(owningType, getNativeType(), elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public Object getConstantValue() {
        return variableElement.getConstantValue();
    }

    @NonNull
    @Override
    public ClassElement getType() {
        if (type == null) {
            type = newClassElement(getNativeType(), variableElement.asType(), Collections.emptyMap());
            if (canBeMarkedWithNonNull(type)) {
                type.getTypeAnnotationMetadata().annotate(NonNull.class);
            }
        }
        return type;
    }

    @NonNull
    @Override
    public ClassElement getGenericType() {
        if (genericType == null) {
            genericType = newClassElement(getNativeType(), variableElement.asType(), getDeclaringType().getTypeArguments());
            if (canBeMarkedWithNonNull(genericType)) {
                genericType.getTypeAnnotationMetadata().annotate(NonNull.class);
            }
        }
        return this.genericType;
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
    public ClassElement getDeclaringType() {
        if (resolvedDeclaringClass == null) {
            Element enclosingElement = variableElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement te) {
                String typeName = JavaModelUtils.getClassName(te);
                if (owningType.getName().equals(JavaModelUtils.getClassName(te))) {
                    resolvedDeclaringClass = owningType;
                } else {
                    TypeMirror returnType = te.asType();
                    Map<String, ClassElement> genericsInfo = owningType.getTypeArguments(typeName);
                    resolvedDeclaringClass = newClassElement(returnType, genericsInfo);
                }
            } else {
                return owningType;
            }
        }
        return resolvedDeclaringClass;
    }

    @Override
    public boolean hides(@NonNull MemberElement hidden) {
        if (isStatic() && getDeclaringType().isInterface()) {
            return false;
        }
        return visitorContext.getElements().hides(getNativeType().element(), ((JavaNativeElement.Variable) hidden.getNativeType()).element());
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }
}
