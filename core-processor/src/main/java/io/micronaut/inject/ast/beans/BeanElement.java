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
package io.micronaut.inject.ast.beans;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Models a bean that will be produced by Micronaut.
 *
 * @since 3.0.0
 * @author graemerocher
 */
public interface BeanElement extends Element {

    /**
     * Returns all of the injection points for the bean. These
     * will be a combination of {@link io.micronaut.inject.ast.FieldElement} and {@link io.micronaut.inject.ast.MethodElement} instances.
     *
     * @return The injection points for the bean.
     */
    @NonNull
    Collection<Element> getInjectionPoints();

    /**
     * @return The originating element.
     */
    @NonNull
    Element getOriginatingElement();

    /**
     * Returns the declaring {@link ClassElement} which may differ
     * from the {@link #getBeanTypes()} in the case of factory beans.
     *
     * @return The declaring class of the bean.
     */
    @NonNull
    ClassElement getDeclaringClass();

    /**
     * The element that produces the bean, this could be a {@link ClassElement} for regular beans,
     * or either a {@link io.micronaut.inject.ast.MethodElement} or {@link io.micronaut.inject.ast.FieldElement} for factory beans.
     *
     * @return The producing element
     */
    @NonNull
    Element getProducingElement();

    /**
     * The type names produced by the bean.
     * @return A set of types
     */
    @NonNull
    Set<ClassElement> getBeanTypes();

    /**
     * The scope of the bean.
     * @return The fully qualified name of the scope or empty if no scope is defined.
     */
    @NonNull
    Optional<String> getScope();

    /**
     * @return One or more fully qualified qualifier types defined by the bean.
     */
    @NonNull
    Collection<String> getQualifiers();

    /**
     * This method adds an associated bean using this class element as the originating element.
     *
     * <p>Note that this method can only be called on classes being directly compiled by Micronaut. If the ClassElement is
     * loaded from pre-compiled code an {@link UnsupportedOperationException} will be thrown.</p>
     * @param type The type of the bean
     * @param visitorContext The visitor context
     * @return A bean builder
     */
    default @NonNull
    BeanElementBuilder addAssociatedBean(@NonNull ClassElement type, @NonNull VisitorContext visitorContext) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding associated beans at compilation time");
    }
}
