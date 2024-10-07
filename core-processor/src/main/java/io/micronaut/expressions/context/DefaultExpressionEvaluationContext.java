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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.micronaut.inject.ast.ElementQuery.ALL_METHODS;
import static java.util.function.Predicate.not;

/**
 * Default implementation of {@link ExtensibleExpressionEvaluationContext}. Extending
 * this context will always return new instance instead of modifying the existing one.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public class DefaultExpressionEvaluationContext implements ExtensibleExpressionEvaluationContext {

    private final Collection<ClassElement> classElements;
    private final MethodElement methodElement;

    private final ClassElement thisType;

    DefaultExpressionEvaluationContext(ClassElement... classElements) {
        this(null, null, classElements);
    }

    private DefaultExpressionEvaluationContext(ClassElement thisType,
                                               MethodElement methodElement,
                                               ClassElement... classElements) {
        this.thisType = thisType;
        this.methodElement = methodElement;
        this.classElements = Arrays.asList(classElements);
    }

    @Override
    public ExtensibleExpressionEvaluationContext withThis(ClassElement classElement) {
        return new DefaultExpressionEvaluationContext(
            classElement,
            methodElement,
            classElements.toArray(ClassElement[]::new)
        );
    }

    @Override
    public DefaultExpressionEvaluationContext extendWith(MethodElement methodElement) {
        ClassElement resolvedThis = methodElement.isStatic() || methodElement instanceof ConstructorElement ? null : methodElement.getOwningType();
        return new DefaultExpressionEvaluationContext(
            resolvedThis,
            methodElement,
            classElements.toArray(ClassElement[]::new)
        );
    }

    @Override
    public DefaultExpressionEvaluationContext extendWith(ClassElement classElement) {
        return new DefaultExpressionEvaluationContext(
            this.thisType,
            this.methodElement,
            ArrayUtils.concat(classElements.toArray(ClassElement[]::new), classElement)
        );
    }

    @Override
    public ClassElement findThis() {
        return thisType;
    }

    @Override
    public List<MethodElement> findMethods(String name) {
        return classElements.stream()
                   .flatMap(element -> findMatchingMethods(element, name).stream())
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
    public List<PropertyElement> findProperties(String name) {
        return classElements.stream()
                   .flatMap(classElement -> getNamedProperties(classElement, name).stream())
                   .toList();
    }

    @Override
    public List<ParameterElement> findParameters(String name) {
        if (this.methodElement == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(methodElement.getParameters())
                   .filter(parameter -> parameter.getName().equals(name))
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
