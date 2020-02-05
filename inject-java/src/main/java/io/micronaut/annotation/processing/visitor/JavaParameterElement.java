/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
class JavaParameterElement extends AbstractJavaElement implements ParameterElement {

    private final JavaVisitorContext visitorContext;
    private final JavaClassElement declaringClass;

    /**
     * Default constructor.
     *
     * @param declaringClass     The declaring class
     * @param element            The variable element
     * @param annotationMetadata The annotation metadata
     * @param visitorContext     The visitor context
     */
    JavaParameterElement(JavaClassElement declaringClass, VariableElement element, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        super(element, annotationMetadata, visitorContext);
        this.declaringClass = declaringClass;
        this.visitorContext = visitorContext;
    }

    @Override
    @NonNull
    public ClassElement getType() {
        TypeMirror parameterType = getNativeType().asType();
        return mirrorToClassElement(parameterType, visitorContext);
    }

    @NonNull
    @Override
    public ClassElement getGenericType() {
        TypeMirror returnType = getNativeType().asType();
        Map<String, Map<String, TypeMirror>> declaredGenericInfo = declaringClass.getGenericTypeInfo();
        return parameterizedClassElement(returnType, visitorContext, declaredGenericInfo);
    }

    @Override
    public VariableElement getNativeType() {
        return (VariableElement) super.getNativeType();
    }
}
