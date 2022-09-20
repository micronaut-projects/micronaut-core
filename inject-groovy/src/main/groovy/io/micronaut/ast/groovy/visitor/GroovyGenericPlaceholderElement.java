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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import org.codehaus.groovy.ast.ClassNode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Implementation of {@link io.micronaut.inject.ast.GenericPlaceholderElement} for Groovy.
 *
 * @author Jonas Konrad
 * @since 3.1.0
 */
@Internal
final class GroovyGenericPlaceholderElement extends GroovyClassElement implements GenericPlaceholderElement {

    GroovyGenericPlaceholderElement(GroovyVisitorContext visitorContext,
                                    ClassNode classNode,
                                    ElementAnnotationMetadataFactory annotationMetadataFactory,
                                    int arrayDimensions) {
        super(visitorContext, classNode, annotationMetadataFactory, null, arrayDimensions);
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBounds() {
        // this is a hack: .redirect() follows the entire chain of redirects, but using this approach, we can only go
        // one down.
        ClassNode singleRedirect = this.classNode.asGenericsType().getUpperBounds()[0];
        return Collections.singletonList(toClassElement(singleRedirect));
    }

    @NonNull
    @Override
    public String getVariableName() {
        return classNode.getUnresolvedName();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        return Optional.empty();
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyGenericPlaceholderElement(visitorContext, classNode, elementAnnotationMetadataFactory, arrayDimensions);
    }

    @Override
    public ClassElement foldBoundGenericTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        Objects.requireNonNull(fold, "Function argument cannot be null");
        return fold.apply(this);
    }
}
