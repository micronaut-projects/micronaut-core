/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.configuration.picocli.annotation;

import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import picocli.CommandLine;

/**
 * Makes commands introspected.
 *
 * @author graemerocher
 * @since 1.1
 */
public class PicocliTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.getAnnotationNames().stream().anyMatch(n -> n.startsWith(CommandLine.class.getName()))) {
            element.annotate(ReflectiveAccess.class);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (element.getAnnotationNames().stream().anyMatch(n -> n.startsWith(CommandLine.class.getName()))) {
            element.annotate(ReflectiveAccess.class);
        }
    }
}
