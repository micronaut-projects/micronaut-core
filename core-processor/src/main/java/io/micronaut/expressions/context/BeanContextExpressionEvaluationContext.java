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
package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.TypedElement;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static io.micronaut.inject.ast.ElementQuery.ALL_METHODS;
import static java.util.function.Predicate.not;

/**
 * Root expression evaluation context is a context which elements are
 * registered in and obtained through bean context.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public final class BeanContextExpressionEvaluationContext implements ExpressionEvaluationContext {

    private final Set<ClassElement> contextTypes = ConcurrentHashMap.newKeySet();

    public BeanContextExpressionEvaluationContext(ClassElement... contextClasses) {
        Arrays.stream(contextClasses)
            .filter(classElement -> !(classElement instanceof AnnotationElement))
            .forEach(contextTypes::add);
    }

    /**
     * @return set of class elements registered in this evaluation context.
     */
    public Set<ClassElement> getContextTypes() {
        return contextTypes;
    }

    @Override
    public List<MethodElement> getMethods(String name) {
        return contextTypes.stream()
                   .map(element -> findMatchingMethods(element, name))
                   .flatMap(Collection::stream)
                   .toList();
    }

    private List<MethodElement> findMatchingMethods(ClassElement classElement, String name) {
        String propertyName = NameUtils.getPropertyNameForGetter(name,
            PropertyElementQuery.of(classElement.getAnnotationMetadata())
                .getReadPrefixes());

        return Stream.concat(
                classElement.getEnclosedElements(ALL_METHODS.onlyAccessible().named(name)).stream(),
                getNamedProperties(classElement, propertyName).stream()
                    .map(PropertyElement::getReadMethod)
                    .flatMap(Optional::stream))
                   .distinct()
                   .filter(method -> method.getSimpleName().equals(name))
                   .toList();
    }

    @Override
    public List<? extends TypedElement> getTypedElements(String name) {
        return contextTypes.stream()
                   .flatMap(classElement -> getNamedProperties(classElement, name).stream())
                   .toList();
    }

    private List<PropertyElement> getNamedProperties(ClassElement classElement, String name) {
        return classElement.getBeanProperties(
                PropertyElementQuery.of(classElement.getAnnotationMetadata())
                    .includes(Collections.singleton(name)))
                   .stream()
                   .filter(not(PropertyElement::isExcluded))
                   .toList();
    }
}
