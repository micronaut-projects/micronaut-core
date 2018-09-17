/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.MethodElement;
import io.micronaut.inject.visitor.ParameterElement;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.function.Function;

/**
 * A method element returning data from a {@link ExecutableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
class JavaMethodElement extends AbstractJavaElement implements MethodElement {

    private final ExecutableElement executableElement;
    private final JavaVisitorContext visitorContext;

    /**
     * @param executableElement  The {@link ExecutableElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    JavaMethodElement(
            ExecutableElement executableElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext) {
        super(executableElement, annotationMetadata);
        this.executableElement = executableElement;
        this.visitorContext = visitorContext;
    }

    @Override
    public ClassElement getReturnType() {
        TypeMirror returnType = executableElement.getReturnType();
        return mirrorToClassElement(returnType, visitorContext);
    }

    @Override
    public ParameterElement[] getParameters() {
        List<? extends VariableElement> parameters = executableElement.getParameters();
        return parameters.stream().map((Function<VariableElement, ParameterElement>) variableElement ->
                new JavaParameterElement(variableElement, visitorContext.getAnnotationUtils().getAnnotationMetadata(variableElement), visitorContext)
        ).toArray(ParameterElement[]::new);
    }
}
