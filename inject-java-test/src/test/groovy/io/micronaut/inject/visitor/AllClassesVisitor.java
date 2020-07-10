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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.ArrayList;
import java.util.List;

public class AllClassesVisitor implements TypeElementVisitor<Object, Object> {

    private List<MethodElement> visitedMethodElements = new ArrayList<>();
    private List<ClassElement> visitedClassElements = new ArrayList<>();

    public AllClassesVisitor() {
        reset();
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    public void reset() {
        visitedMethodElements.clear();
        visitedClassElements.clear();
    }

    public List<MethodElement> getVisitedMethodElements() {
        return visitedMethodElements;
    }

    public List<ClassElement> getVisitedClassElements() {
        return visitedClassElements;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visitedClassElements.add(element);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        visitedMethodElements.add(element);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
    }
}

