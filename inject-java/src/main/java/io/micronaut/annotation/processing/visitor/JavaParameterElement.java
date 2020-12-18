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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

/**
 * Implementation of the {@link ParameterElement} interface for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class JavaParameterElement extends AbstractJavaElement implements ParameterElement {

    private final JavaVisitorContext visitorContext;
    private final JavaClassElement declaringClass;
    private ClassElement typeElement;
    private ClassElement genericTypeElement;

    /**
     * Default constructor.
     *
     * @param declaringClass     The declaring class
     * @param element            The variable element
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     */
    public JavaParameterElement(JavaClassElement declaringClass, VariableElement element, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        super(element, annotationMetadata, visitorContext);
        this.declaringClass = declaringClass;
        this.visitorContext = visitorContext;
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
            TypeMirror parameterType = getNativeType().asType();
            this.typeElement = mirrorToClassElement(parameterType, visitorContext);
        }
        return typeElement;
    }

    @NonNull
    @Override
    public ClassElement getGenericType() {
        if (this.genericTypeElement == null) {
            TypeMirror returnType = getNativeType().asType();
            Map<String, Map<String, TypeMirror>> declaredGenericInfo = declaringClass.getGenericTypeInfo();
            this.genericTypeElement = parameterizedClassElement(returnType, visitorContext, declaredGenericInfo);
        }
        return this.genericTypeElement;
    }

    @Override
    public VariableElement getNativeType() {
        return (VariableElement) super.getNativeType();
    }
}
