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
package io.micronaut.inject.visitor;

import io.micronaut.http.annotation.Get;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.*;

public class AllClassesVisitor implements TypeElementVisitor<Object, Get> {
    private static List<String> VISITED_ELEMENTS = new ArrayList<>();
    private static Map<VisitorContext, Boolean> started = new LinkedHashMap<>();
    private static Map<VisitorContext, Boolean> finished = new LinkedHashMap<>();

    public static List<String> getVisited() {
        return Collections.unmodifiableList(VISITED_ELEMENTS);
    }

    public static void clearVisited() {
        VISITED_ELEMENTS = new ArrayList<>();
    }

    @Override
    public void start(VisitorContext visitorContext) {
        if (started.containsKey(visitorContext)) {
            throw new RuntimeException("Started should be null");
        }
        started.put(visitorContext, true);
    }


    @Override
    public void finish(VisitorContext visitorContext) {
        if (finished.containsKey(visitorContext)) {
            throw new RuntimeException("Finished should be null");
        }
        finished.put(visitorContext, true);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visit(element);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        visit(element);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        visit(element);
    }

    void visit(Element element) {
        VISITED_ELEMENTS.add(element.getName());
    }
}
