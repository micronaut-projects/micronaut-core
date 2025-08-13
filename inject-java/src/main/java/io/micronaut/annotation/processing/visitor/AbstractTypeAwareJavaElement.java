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
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.Element;

/**
 * An abstract class that is aware of the use-type.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public abstract class AbstractTypeAwareJavaElement extends AbstractJavaElement {

    /**
     * The constructor.
     *
     * @param nativeElement             The {@link Element}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The Java visitor context
     */
    AbstractTypeAwareJavaElement(JavaNativeElement nativeElement, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
    }

    /**
     * Checks if the element is explicitly marked as null-marked.
     *
     * @return true if the element is marked as null-marked, false otherwise
     */
    protected abstract boolean hasNullMarked();

    /**
     * Retrieves the metadata of annotations associated with the type of the current element.
     *
     * @return the {@link AnnotationMetadata} representing the annotations of the type.
     */
    protected abstract AnnotationMetadata getTypeAnnotationMetadata();

    @Override
    public final boolean isDeclaredNullable() {
        return getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.NULLABLE)
            || getTypeAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.NULLABLE);
    }

    @Override
    public final boolean isNullable() {
        return getAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE)
            || getTypeAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE);
    }

    @Override
    public final boolean isNonNull() {
        return getAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL)
            || getTypeAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL)
            || hasNullMarked() && !isNullable();
    }

    @Override
    public final boolean isDeclaredNonNull() {
        return getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.NON_NULL)
            || getTypeAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.NON_NULL);
    }

    protected boolean canBeMarkedWithNonNull(ClassElement classElement) {
        return !classElement.isVoid()
            && !classElement.isPrimitive()
            && !classElement.isNullable()
            && hasNullMarked();
    }
}
