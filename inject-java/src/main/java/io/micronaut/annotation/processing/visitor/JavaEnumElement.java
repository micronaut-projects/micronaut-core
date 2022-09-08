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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements the {@link EnumElement} interface for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class JavaEnumElement extends JavaClassElement implements EnumElement {

    protected List<EnumConstantElement> enumConstants;
    protected List<String> values;

    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    JavaEnumElement(TypeElement classElement,
                    ElementAnnotationMetadataFactory annotationMetadataFactory,
                    JavaVisitorContext visitorContext) {
        this(classElement, annotationMetadataFactory, visitorContext, 0);
    }

    /**
     * @param classElement              The {@link TypeElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     * @param arrayDimensions           The number of array dimensions
     */
    JavaEnumElement(TypeElement classElement,
                    ElementAnnotationMetadataFactory annotationMetadataFactory,
                    JavaVisitorContext visitorContext,
                    int arrayDimensions) {
        super(classElement, annotationMetadataFactory, visitorContext, Collections.emptyList(), Collections.emptyMap(), arrayDimensions, false);
    }

    @Override
    public List<String> values() {
        if (values != null) {
            return values;
        }
        initEnum();
        return values;
    }

    @Override
    public List<EnumConstantElement> elements() {
        if (enumConstants != null) {
            return enumConstants;
        }
        initEnum();
        return enumConstants;
    }

    private void initEnum() {
        values = new ArrayList<>();
        enumConstants = new ArrayList<>();
        TypeElement nativeType = (TypeElement) getNativeType();
        for (Element element : nativeType.getEnclosedElements()) {
            if (element.getKind() == ElementKind.ENUM_CONSTANT) {
                values.add(element.getSimpleName().toString());
                enumConstants.add(
                    new JavaEnumConstantElement(
                        this,
                        (VariableElement) element,
                        elementAnnotationMetadataFactory,
                        visitorContext)
                );
            }
        }
        values = Collections.unmodifiableList(values);
        enumConstants = Collections.unmodifiableList(enumConstants);
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new JavaEnumElement(classElement, elementAnnotationMetadataFactory, visitorContext, arrayDimensions);
    }
}
