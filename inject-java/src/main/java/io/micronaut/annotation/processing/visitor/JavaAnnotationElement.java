/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an annotation in the AST for Java.
 *
 * @author graemerocher
 * @since 3.1.0
 */
@Internal
final class JavaAnnotationElement extends JavaClassElement implements AnnotationElement {

    /**
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext The visitor context
     */
    JavaAnnotationElement(JavaNativeElement.Class nativeElement,
                          ElementAnnotationMetadataFactory annotationMetadataFactory,
                          JavaVisitorContext visitorContext) {
        super(nativeElement, annotationMetadataFactory, visitorContext);
    }

    @Override
    protected JavaClassElement copyThis() {
        // an annotation type cannot be generic, so the type arguments can be ignored
        return new JavaAnnotationElement(getNativeType(), elementAnnotationMetadataFactory, visitorContext);
    }

    @Override
    public boolean isInherited() {
        TypeElement typeElement = getNativeType().element();
        for (AnnotationMirror annotationMirror : typeElement.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement() instanceof TypeElement annotationType
                && AnnotationUtil.ANN_INHERITED.contentEquals(annotationType.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<ElementType> getTargets() {
        TypeElement typeElement = getNativeType().element();
        for (AnnotationMirror annotationMirror : typeElement.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement() instanceof TypeElement annotationType
                && Target.class.getName().contentEquals(annotationType.getQualifiedName())) {
                EnumSet<ElementType> targets = EnumSet.noneOf(ElementType.class);
                for (AnnotationValue annotationValue : annotationMirror.getElementValues().values()) {
                    addTargets(annotationValue.getValue(), targets);
                }
                return Collections.unmodifiableSet(targets);
            }
        }
        return DEFAULT_TARGETS;
    }

    private static void addTargets(Object value, EnumSet<ElementType> targets) {
        if (value instanceof List<?> values) {
            for (Object item : values) {
                addTargets(item, targets);
            }
        } else if (value instanceof AnnotationValue annotationValue) {
            addTargets(annotationValue.getValue(), targets);
        } else if (value instanceof VariableElement constant) {
            String name = constant.getSimpleName().toString();
            for (ElementType elementType : ElementType.values()) {
                if (elementType.name().equals(name)) {
                    targets.add(elementType);
                    break;
                }
            }
        }
    }

    @Override
    public Optional<String> getRepeatableContainer() {
        return Optional.ofNullable(
            visitorContext.getAnnotationMetadataBuilder().getRepeatableContainerNameForType(getNativeType().element())
        );
    }

    @Override
    public RetentionPolicy getRetentionPolicy() {
        return visitorContext.getAnnotationMetadataBuilder().getRetentionPolicy(getNativeType().element());
    }
}
