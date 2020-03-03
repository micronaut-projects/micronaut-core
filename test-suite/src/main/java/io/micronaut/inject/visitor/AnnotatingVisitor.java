/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.visitor;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

// tests that dynamic annotation works
public class AnnotatingVisitor implements TypeElementVisitor<Version, Version> {

    public static final String ANN_NAME = TestAnn.class.getName();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        context.info("Annotating type", element);
        element.annotate(TestAnn.class, (builder) -> builder.value("class"));
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        context.info("Annotating method", element);
        element.annotate(TestAnn.class, (builder) -> builder.value("method"));
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        context.info("Annotating constructor", element);
        element.annotate(TestAnn.class, (builder) -> builder.value("constructor"));
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        context.info("Annotating field", element);
        // test using name
        element.annotate(TestAnn.class.getName(), (builder) -> builder.value("field"));
    }
}
