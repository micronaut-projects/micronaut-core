/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.AbstractJavaElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Java element annotation metadata factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class JavaElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<Element, AnnotationMirror> {

    private static final ElementAnnotationMetadata EMPTY = new ElementAnnotationMetadata() {
    };

    public JavaElementAnnotationMetadataFactory(boolean isReadOnly, JavaAnnotationMetadataBuilder metadataBuilder) {
        super(isReadOnly, metadataBuilder);
    }

    @Override
    public ElementAnnotationMetadataFactory readOnly() {
        return new JavaElementAnnotationMetadataFactory(true, (JavaAnnotationMetadataBuilder) metadataBuilder);
    }

    @Override
    public ElementAnnotationMetadata build(io.micronaut.inject.ast.Element element) {
        AbstractJavaElement javaElement = (AbstractJavaElement) element;
        if (notAllowedAnnotations(javaElement)) {
            return EMPTY;
        }
        return super.build(element);
    }

    private static boolean notAllowedAnnotations(AbstractJavaElement javaElement) {
        return !(javaElement.getNativeType() instanceof Element);
    }

    @Override
    public ElementAnnotationMetadata build(io.micronaut.inject.ast.Element element, AnnotationMetadata defaultAnnotationMetadata) {
        if (defaultAnnotationMetadata == null) {
            AbstractJavaElement javaElement = (AbstractJavaElement) element;
            if (notAllowedAnnotations(javaElement)) {
                return EMPTY;
            }
        }
        return super.build(element, defaultAnnotationMetadata);
    }
}
