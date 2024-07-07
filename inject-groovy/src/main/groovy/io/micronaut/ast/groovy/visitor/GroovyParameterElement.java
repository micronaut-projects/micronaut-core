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
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
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
     * @param nativeElement             The nativeElement
     * @param parameter                 The parameter
     * @param elementAnnotationMetadata The annotation metadata
     */
    GroovyParameterElement(GroovyMethodElement methodElement,
                           GroovyVisitorContext visitorContext,
                           GroovyNativeElement nativeElement,
                           Parameter parameter,
                           ElementAnnotationMetadataFactory elementAnnotationMetadata) {
        super(visitorContext, nativeElement, elementAnnotationMetadata);
        this.parameter = parameter;
        this.methodElement = methodElement;
    }

    @Override
    protected AbstractGroovyElement copyConstructor() {
        return new GroovyParameterElement(methodElement, visitorContext, getNativeType(), parameter, elementAnnotationMetadataFactory);
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

    @Override
    public @NonNull ClassElement getGenericType() {
        if (genericType == null) {
            genericType = newClassElement(parameter.getType(), methodElement.getTypeArguments());
        }
        return genericType;
    }

    @Override
    public @NonNull String getName() {
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
    public GroovyMethodElement getMethodElement() {
        return methodElement;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        if (typeElement == null) {
            typeElement = newClassElement(parameter.getType());
        }
        return typeElement;
    }

}
