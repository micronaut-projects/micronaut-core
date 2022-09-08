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
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;

import javax.lang.model.element.PackageElement;

/**
 * A package element for Java.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@Internal
public class JavaPackageElement extends AbstractJavaElement implements io.micronaut.inject.ast.PackageElement {

    private final PackageElement element;

    /**
     * @param element                   The {@link PackageElement}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The Java visitor context
     */
    public JavaPackageElement(PackageElement element,
                              ElementAnnotationMetadataFactory annotationMetadataFactory,
                              JavaVisitorContext visitorContext) {
        super(element, annotationMetadataFactory, visitorContext);
        this.element = element;
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaPackageElement(element, elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public String getName() {
        return element.getQualifiedName().toString();
    }

    @Override
    public String getSimpleName() {
        return element.getSimpleName().toString();
    }

    @Override
    public boolean isUnnamed() {
        return element.isUnnamed();
    }
}
