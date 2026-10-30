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
package io.micronaut.inject.visitor.producebeans;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Visiting a class named {@value #PRODUCERS}, adds a bean of it that produces a bean from each of its declared methods
 * and fields whose name starts with {@code produce}, named after the member, whatever its visibility and the type it
 * produces.
 */
public class ProduceBeansVisitor implements TypeElementVisitor<Object, Object> {

    public static final String PRODUCERS = "AccessProducers";

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.getSimpleName().equals(PRODUCERS)) {
            element.addAssociatedBean(element)
                .produceBeans(ElementQuery.ALL_METHODS.onlyDeclared().named(name -> name.startsWith("produce")), ProduceBeansVisitor::named)
                .produceBeans(ElementQuery.ALL_FIELDS.onlyDeclared().named(name -> name.startsWith("produce")), ProduceBeansVisitor::named);
        }
    }

    private static void named(BeanElementBuilder child) {
        child.annotate(AnnotationUtil.NAMED, builder -> builder.value(((MemberElement) child.getProducingElement()).getName()));
    }
}
