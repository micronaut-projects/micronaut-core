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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.PropertyElement;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Models a {@link PropertyElement} for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class JavaPropertyElement extends AbstractJavaElement implements PropertyElement {

    private final String name;
    private final ClassElement type;
    private final boolean readOnly;
    private final ClassElement declaringElement;
    private final JavaVisitorContext visitorContext;
    private final List<Element> nativeParents;

    /**
     * Default constructor.
     *
     * @param declaringElement          The declaring element
     * @param nativeParents             The native elements that property is representing
     * @param rootElement               The element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param name                      The name
     * @param type                      The type
     * @param readOnly                  Whether it is read only
     * @param visitorContext            The java visitor context
     */
    JavaPropertyElement(ClassElement declaringElement,
                        List<Element> nativeParents,
                        Element rootElement,
                        ElementAnnotationMetadataFactory annotationMetadataFactory,
                        String name,
                        ClassElement type,
                        boolean readOnly,
                        JavaVisitorContext visitorContext) {
        super(rootElement, annotationMetadataFactory, visitorContext);
        this.nativeParents = nativeParents;
        this.name = name;
        this.type = type;
        this.readOnly = readOnly;
        this.declaringElement = declaringElement;
        this.visitorContext = visitorContext;
    }

    @Override
    public ClassElement getGenericType() {
        Map<String, Map<String, TypeMirror>> declaredGenericInfo;
        if (declaringElement instanceof JavaClassElement) {
            declaredGenericInfo = ((JavaClassElement) declaringElement).getGenericTypeInfo();
        } else {
            declaredGenericInfo = Collections.emptyMap();
        }
        return parameterizedClassElement(((TypeElement) type.getNativeType()).asType(), visitorContext, declaredGenericInfo);
    }

    @Override
    public boolean isPrimitive() {
        return type.isPrimitive();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public int getArrayDimensions() {
        return type.getArrayDimensions();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return name;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringElement;
    }

    @Override
    public List<Element> getNativeParents() {
        return nativeParents;
    }
}
