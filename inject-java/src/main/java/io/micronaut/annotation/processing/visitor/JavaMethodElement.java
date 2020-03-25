/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A method element returning data from a {@link ExecutableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
class JavaMethodElement extends AbstractJavaElement implements MethodElement {

    private final ExecutableElement executableElement;
    private final JavaVisitorContext visitorContext;
    private final JavaClassElement declaringClass;

    /**
     * @param declaringClass     The declaring class
     * @param executableElement  The {@link ExecutableElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    JavaMethodElement(
            JavaClassElement declaringClass,
            ExecutableElement executableElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext) {
        super(executableElement, annotationMetadata, visitorContext);
        this.executableElement = executableElement;
        this.visitorContext = visitorContext;
        this.declaringClass = declaringClass;
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        Map<String, Map<String, TypeMirror>> info = declaringClass.getGenericTypeInfo();
        return mirrorToClassElement(executableElement.getReturnType(), visitorContext, info);
    }

    @Override
    public Optional<String> getDocumentation() {
        return Optional.ofNullable(visitorContext.getElements().getDocComment(executableElement));
    }

    @Override
    @NonNull
    public ClassElement getReturnType() {
        TypeMirror returnType = executableElement.getReturnType();
        return mirrorToClassElement(returnType, visitorContext, Collections.emptyMap());
    }

    @Override
    public ParameterElement[] getParameters() {
        List<? extends VariableElement> parameters = executableElement.getParameters();
        return parameters.stream().map((Function<VariableElement, ParameterElement>) variableElement -> {
                    AnnotationMetadata annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(variableElement);
                    if (annotationMetadata.hasDeclaredAnnotation("org.jetbrains.annotations.Nullable")) {
                        annotationMetadata = DefaultAnnotationMetadata.mutateMember(annotationMetadata, "javax.annotation.Nullable", Collections.emptyMap());
                    }
                    return new JavaParameterElement(declaringClass, variableElement, annotationMetadata, visitorContext);
                }
        ).toArray(ParameterElement[]::new);
    }

    @Override
    public ClassElement getDeclaringType() {
        Element enclosingElement = executableElement.getEnclosingElement();
        if (enclosingElement instanceof TypeElement) {
            TypeElement te = (TypeElement) enclosingElement;
            if (declaringClass.getName().equals(te.getQualifiedName().toString())) {
                return declaringClass;
            } else {
                return new JavaClassElement(
                        te,
                        visitorContext.getAnnotationUtils().getAnnotationMetadata(te),
                        visitorContext,
                        declaringClass.getGenericTypeInfo()
                );
            }
        } else {
            return declaringClass;
        }
    }

    @Override
    public ClassElement getOwningType() {
        return declaringClass;
    }
}
