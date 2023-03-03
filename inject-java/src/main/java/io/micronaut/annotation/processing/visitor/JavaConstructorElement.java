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
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

/**
 * A {@link ConstructorElement} for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class JavaConstructorElement extends JavaMethodElement implements ConstructorElement {

    /**
     * @param owningClass            The declaring class
     * @param nativeElement             The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The visitor context
     */
    JavaConstructorElement(JavaClassElement owningClass,
                           JavaNativeElement.Method nativeElement,
                           ElementAnnotationMetadataFactory annotationMetadataFactory,
                           JavaVisitorContext visitorContext) {
        super(owningClass, nativeElement, annotationMetadataFactory, visitorContext);
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaConstructorElement(getDeclaringType(), getNativeType(), elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public boolean overrides(MethodElement overridden) {
        return false;
    }

    @Override
    public boolean hides(MemberElement hidden) {
        return false;
    }
}
