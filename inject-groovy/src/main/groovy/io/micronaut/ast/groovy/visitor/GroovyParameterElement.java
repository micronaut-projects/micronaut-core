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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ParameterElement;
import org.codehaus.groovy.ast.Parameter;

/**
 * Implementation of {@link ParameterElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class GroovyParameterElement extends AbstractGroovyElement implements ParameterElement {

    private final Parameter parameter;
    private final GroovyMethodElement methodElement;
    private ClassElement typeElement;
    private ClassElement genericType;

    /**
     * Default constructor.
     *
     * @param methodElement             The parent method element
     * @param visitorContext            The visitor context
     * @param parameter                 The parameter
     * @param elementAnnotationMetadata The annotation metadata
     */
    GroovyParameterElement(GroovyMethodElement methodElement,
                           GroovyVisitorContext visitorContext,
                           Parameter parameter,
                           ElementAnnotationMetadataFactory elementAnnotationMetadata) {
        super(visitorContext, parameter, elementAnnotationMetadata);
        this.parameter = parameter;
        this.methodElement = methodElement;
    }

    @Override
    protected AbstractGroovyElement copyThis() {
        return new GroovyParameterElement(methodElement, visitorContext, parameter, elementAnnotationMetadataFactory);
    }

    @Override
    public ParameterElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ParameterElement) super.withAnnotationMetadata(annotationMetadata);
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

    @Nullable
    @Override
    public ClassElement getGenericType() {
        if (this.genericType == null) {
            ClassElement type = getType();
            this.genericType = methodElement.getGenericElement(parameter.getType(), type);
        }
        return this.genericType;
    }

    @Override
    public String getName() {
        return parameter.getName();
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Parameter getNativeType() {
        return parameter;
    }

    @Override
    public GroovyMethodElement getMethodElement() {
        return methodElement;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        if (this.typeElement == null) {
            this.typeElement = visitorContext.getElementFactory().newClassElement(parameter.getType(), elementAnnotationMetadataFactory);
        }
        return this.typeElement;
    }

}
