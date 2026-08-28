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
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TestAggregatingVisitor implements TypeElementVisitor<TestAggregate, Object> {
    private final Set<String> visited = new LinkedHashSet<>();
    private boolean written;

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(TestAggregate.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visited.add(element.getName());
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (visited.isEmpty() || written) {
            return;
        }
        written = true;
        visitorContext.visitGeneratedFile("META-INF/pyronaut/aggregating.txt")
            .ifPresent(file -> {
                try (var writer = file.openWriter()) {
                    for (String name : visited) {
                        writer.write(name);
                        writer.write(System.lineSeparator());
                    }
                } catch (IOException e) {
                    visitorContext.fail("Failed to write aggregating test output: " + e.getMessage(), null);
                }
            });
    }
}
