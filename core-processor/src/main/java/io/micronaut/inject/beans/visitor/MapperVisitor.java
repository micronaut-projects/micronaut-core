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
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;
import java.util.Set;

public final class MapperVisitor implements TypeElementVisitor<Object, Mapper> {
    private ClassElement lastClassElement;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(Mapper.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        lastClassElement = element;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Mapper.class)) {
            if (element.isAbstract()) {

                ClassElement toType = element.getGenericReturnType();
                @NonNull ParameterElement[] parameters = element.getParameters();
                if (parameters.length == 1) {
                    if (toType.isVoid()) {
                        context.fail("A void return type is not permitted for a mapper", element);
                    } else {

                        List<AnnotationValue<Mapper.Mapping>> values = element.getAnnotationMetadata().getAnnotationValuesByType(Mapper.Mapping.class);
                        if (CollectionUtils.isNotEmpty(values)) {
                            ParameterElement fromType = parameters[0];

                            for (AnnotationValue<Mapper.Mapping> value : values) {
                                value.stringValue("to").ifPresent(to -> {
                                    List<PropertyElement> beanProperties = toType.getBeanProperties(PropertyElementQuery.of(toType).includes(Set.of(to)));
                                    if (beanProperties.isEmpty()) {
                                        context.fail("@Mapping(to=\"" + to + "\") specifies a property that doesn't exist in type " + toType.getName(), element);
                                    }
                                });
                                value.stringValue("from").ifPresent(from -> {
                                    if (!from.contains("#{")) {
                                        ClassElement genericType = fromType.getGenericType();
                                        List<PropertyElement> beanProperties = genericType.getBeanProperties(PropertyElementQuery.of(genericType).includes(Set.of(from)));
                                        if (beanProperties.isEmpty()) {
                                            context.fail("@Mapping(from=\"" + from + "\") specifies a property that doesn't exist in type " + genericType.getName(), element);
                                        }
                                    }
                                });
                            }
                        }
                        if (lastClassElement !=  null) {
                            lastClassElement.annotate(Mapper.class);
                        }
                    }
                } else {
                    context.fail("Only a single parameter is permitted on a mapping method", element);
                }
            } else {
                context.fail("@Mapper can only be declared on abstract methods", element);
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
