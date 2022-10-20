/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.visitors;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.http.annotation.Controller;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.List;

public class AllElementsVisitor implements TypeElementVisitor<Controller, Object> {
    public static List<String> VISITED_ELEMENTS = new ArrayList<>();
    public static List<ClassElement> VISITED_CLASS_ELEMENTS = new ArrayList<>();
    public static List<MethodElement> VISITED_METHOD_ELEMENTS = new ArrayList<>();

    @Override
    public void start(VisitorContext visitorContext) {
        VISITED_ELEMENTS.clear();
        VISITED_CLASS_ELEMENTS.clear();
        VISITED_METHOD_ELEMENTS.clear();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visit(element);
        // Java 9+ doesn't allow resolving elements was the compiler
        // is finished being used so this test cannot be made to work beyond Java 8 the way it is currently written
        element.getBeanProperties(); // Preload properties for tests otherwise it fails because the compiler is done
        element.getAnnotationMetadata();
        VISITED_CLASS_ELEMENTS.add(element);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        VISITED_METHOD_ELEMENTS.add(element);
        element.getReturnType().getBeanProperties().forEach(AnnotationMetadataProvider::getAnnotationMetadata); // Preload
        element.getAnnotationMetadata();
        visit(element);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        visit(element);
        element.getAnnotationMetadata();
    }

    private void visit(Element element) {
        VISITED_ELEMENTS.add(element.getName());
    }
}
