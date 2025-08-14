/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.annotation.NullMarked;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.Element;

/**
 * An abstract class for other elements to extend from.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public abstract class AbstractJavaMemberElement extends AbstractTypeAwareJavaElement implements MemberElement {

    /**
     * The constructor.
     * @param nativeElement             The {@link Element}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The Java visitor context
     */
    AbstractJavaMemberElement(JavaNativeElement nativeElement, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
    }

    @Override
    public MemberElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MemberElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    protected boolean hasNullMarked() {
        if (hasStereotype(NullMarked.class)) {
            return true;
        }
        ClassElement owningType = getOwningType();
        if (owningType instanceof AbstractTypeAwareJavaElement typeAwareJavaElement) {
            return typeAwareJavaElement.hasNullMarked();
        }
        return owningType.hasStereotype(NullMarked.class);
    }

}
