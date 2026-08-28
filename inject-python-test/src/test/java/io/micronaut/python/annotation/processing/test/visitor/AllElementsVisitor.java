/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.annotation.processing.test.visitor;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test visitor that can write a file either under META-INF or as a generated file.
 */
public class AllElementsVisitor implements TypeElementVisitor<Object, Object> {
    public static boolean WRITE_FILE = false;
    public static boolean WRITE_IN_METAINF = false;
    public static final List<ClassElement> VISITED_CLASS_ELEMENTS = new ArrayList<>();
    public static final List<MethodElement> VISITED_METHOD_ELEMENTS = new ArrayList<>();

    @Override
    public void start(VisitorContext visitorContext) {
        VISITED_CLASS_ELEMENTS.clear();
        VISITED_METHOD_ELEMENTS.clear();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        VISITED_CLASS_ELEMENTS.add(element);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        VISITED_METHOD_ELEMENTS.add(element);
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (!WRITE_FILE) {
            return;
        }
        Optional<GeneratedFile> generatedFile;
        if (WRITE_IN_METAINF) {
            generatedFile = visitorContext.visitMetaInfFile("foo/bar.txt", VISITED_CLASS_ELEMENTS.toArray(ClassElement.ZERO_CLASS_ELEMENTS));
        } else {
            generatedFile = visitorContext.visitGeneratedFile("foo/bar.txt", VISITED_CLASS_ELEMENTS.toArray(ClassElement.ZERO_CLASS_ELEMENTS));
        }
        var gf = generatedFile.orElseThrow();
        try (Writer w = gf.openWriter()) {
            w.write("All good");
        } catch (IOException e) {
            visitorContext.fail(e.getMessage(), null);
        } finally {
            // reset for next test run
            WRITE_FILE = false;
            WRITE_IN_METAINF = false;
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
