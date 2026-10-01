/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The metadata returned by the default {@link MethodElement#getMethodAnnotationMetadata()}, for an element
 * that does not override it.
 *
 * <p>It cannot separate the method annotations from the class annotations, so it approximates the read side
 * with {@link MethodElement#getAnnotationMetadata()} and does not support mutation. This is a named class
 * rather than an anonymous one so that the failure names the element at fault: an anonymous class reports
 * itself as {@code MethodElement$1}, which says nothing about which element was not overriding the
 * method.</p>
 *
 * @since 5.2.0
 */
@Internal
final class DefaultMethodAnnotationMetadata implements MutableAnnotationMetadataDelegate<AnnotationMetadata> {

    private static final String ADDING = "adding";
    private static final String REMOVING = "removing";

    private final MethodElement methodElement;

    DefaultMethodAnnotationMetadata(MethodElement methodElement) {
        this.methodElement = methodElement;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return methodElement.getAnnotationMetadata();
    }

    @Override
    public <T extends Annotation> AnnotationMetadata annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        throw unsupported(ADDING);
    }

    @Override
    public <T extends Annotation> AnnotationMetadata annotate(AnnotationValue<T> annotationValue) {
        throw unsupported(ADDING);
    }

    @Override
    public AnnotationMetadata removeAnnotation(String annotationType) {
        throw unsupported(REMOVING);
    }

    @Override
    public <T extends Annotation> AnnotationMetadata removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        throw unsupported(REMOVING);
    }

    @Override
    public AnnotationMetadata removeStereotype(String annotationType) {
        throw unsupported(REMOVING);
    }

    /**
     * The {@code Class}-typed and no-argument overloads all delegate to the methods above, so they are
     * covered by this too.
     */
    private UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("Element of type [" + methodElement.getClass() + "] does not support "
            + operation + " annotations at compilation time through getMethodAnnotationMetadata(), which it does not override");
    }
}
