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
package io.micronaut.python.compiler;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.util.Set;

/**
 * Writes a file for the type of each field and the return type of each method of a class, with that type as the
 * originating element, as a visitor documenting the types of a model does.
 */
public final class TestTypeUseOriginVisitor implements TypeElementVisitor<TestTypeUseOrigin, Object> {
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(TestTypeUseOrigin.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        for (FieldElement field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared())) {
            write(element, field.getName(), field.getType(), context);
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            write(element, method.getName(), method.getReturnType(), context);
        }
    }

    private static void write(ClassElement owner, String member, ClassElement type, VisitorContext context) {
        context.visitMetaInfFile(
            "pyronaut/type-use-" + owner.getSimpleName() + "-" + member + ".txt",
            type
        ).ifPresent(file -> {
            try (var writer = file.openWriter()) {
                writer.write(type.getClass().getSimpleName());
            } catch (IOException e) {
                context.fail("Failed to write type-use test output: " + e.getMessage(), owner);
            }
        });
    }
}
