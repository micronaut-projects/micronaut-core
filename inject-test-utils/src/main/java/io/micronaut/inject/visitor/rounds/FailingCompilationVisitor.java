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
package io.micronaut.inject.visitor.rounds;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails the compilation from {@link #finishCompilation(VisitorContext)}: with {@link VisitorContext#fail} for a class
 * named {@value #FAIL}, and by throwing a {@link ProcessingException} for a class named {@value #THROW}, whatever their
 * package and language.
 */
public class FailingCompilationVisitor implements TypeElementVisitor<Object, Object> {

    public static final String FAIL = "FailInFinishCompilation";
    public static final String THROW = "ThrowInFinishCompilation";
    public static final String FAILED_MESSAGE = "Failed finishing the compilation of ";
    public static final String THROWN_MESSAGE = "Thrown finishing the compilation of ";

    private final List<String> failing = new ArrayList<>();
    private final List<String> throwing = new ArrayList<>();

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.getSimpleName().equals(FAIL)) {
            failing.add(element.getName());
        } else if (element.getSimpleName().equals(THROW)) {
            throwing.add(element.getName());
        }
    }

    @Override
    public void finishCompilation(VisitorContext context) {
        for (String name : failing) {
            context.fail(FAILED_MESSAGE + name, context.getClassElement(name).orElse(null));
        }
        failing.clear();
        if (!throwing.isEmpty()) {
            String name = throwing.get(0);
            throwing.clear();
            throw new ProcessingException(context.getClassElement(name).orElse(null), THROWN_MESSAGE + name);
        }
    }
}
