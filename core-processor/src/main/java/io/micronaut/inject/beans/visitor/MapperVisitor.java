/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.context.annotation.Mapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mapper visitor.
 * @since 4.1.0
 */
public final class MapperVisitor implements TypeElementVisitor<Object, Mapper> {
    private ClassElement lastClassElement;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(Mapper.class.getName());
    }

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyMethods();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        lastClassElement = element;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Mapper.class)) {
            if (!element.isAbstract()) {
                throw new ProcessingException(element, "@Mapper can only be declared on abstract methods");
            }
            ClassElement toType = element.getGenericReturnType();
            if (toType.isVoid()) {
                throw new ProcessingException(element, "A void return type is not permitted for a mapper");
            }

            List<AnnotationValue<Mapper.Mapping>> values = element.getAnnotationMetadata().getAnnotationValuesByType(Mapper.Mapping.class);
            if (!CollectionUtils.isEmpty(values)) {
                validateMappingAnnotations(element, values, toType);
            }
            if (lastClassElement != null) {
                lastClassElement.annotate(Mapper.class);
            }
        }
    }

    @SuppressWarnings("java:S1192")
    private void validateMappingAnnotations(MethodElement element, List<AnnotationValue<Mapper.Mapping>> values, ClassElement toType) {
        @NonNull ParameterElement[] parameters = element.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            ParameterElement parameter = parameters[i];
            ClassElement fromType = parameter.getGenericType();
            boolean isMap = fromType.isAssignable(Map.class);

            if (isMap) {
                List<? extends ClassElement> boundGenerics = fromType.getBoundGenericTypes();
                if (boundGenerics.isEmpty() || !boundGenerics.iterator().next().isAssignable(String.class)) {
                    throw new ProcessingException(element, "@Mapping from parameter that is a Map must have String keys");
                }
            }
        }

        Set<String> toDefs = new HashSet<>();
        for (AnnotationValue<Mapper.Mapping> value : values) {
            value.stringValue("to").ifPresent(to -> {
                if (toDefs.contains(to)) {
                    throw new ProcessingException(element, "Multiple @Mapping definitions map to the same property: " + to);
                } else {
                    toDefs.add(to);
                    if (!hasPropertyWithName(toType, to)) {
                        throw new ProcessingException(element, "@Mapping(to=\"" + to + "\") specifies a property that doesn't exist in type " + toType.getName());
                    }
                }
            });
            value.stringValue("from").ifPresent(from -> {
                if (from.contains("#{")) {
                    return;
                }
                if (from.contains(".")) {
                    int index = from.indexOf(".");
                    String argumentName = from.substring(0, index);
                    String propertyName = from.substring(index + 1);

                    boolean anyMatch = false;
                    for (ParameterElement parameter: parameters) {
                        if (parameter.getName().equals(argumentName)) {
                            anyMatch = true;
                            if (parameter.getType().getName().equals(Map.class.getName())) {
                                break;
                            }
                            if (!hasPropertyWithName(parameter.getGenericType(), propertyName)) {
                                throw new ProcessingException(element, "@Mapping(from=\"" + from + "\") specifies property " + propertyName + " that doesn't exist in type " + parameter.getGenericType().getName());
                            }
                            break;
                        }
                    }
                    if (!anyMatch) {
                        throw new ProcessingException(element, "@Mapping(from=\"" + from + "\") specifies argument " + argumentName + " that doesn't exist for method");
                    }
                } else {
                    String propertyName = from.substring(from.indexOf(".") + 1);
                    for (ParameterElement parameter: parameters) {
                        if (parameter.getType().getName().equals(Map.class.getName())) {
                            continue;
                        }
                        if (!hasPropertyWithName(parameter.getGenericType(), propertyName)) {
                            throw new ProcessingException(element, "@Mapping(from=\"" + from + "\") specifies property " + propertyName + " that doesn't exist in type " + parameter.getGenericType().getName());
                        }
                    }
                }
            });
        }
    }

    private boolean hasPropertyWithName(ClassElement element, String propertyName) {
        return element.getBeanProperties(PropertyElementQuery.of(element).includes(Collections.singleton(propertyName)))
            .stream().anyMatch(v -> !v.isExcluded());
    }

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
