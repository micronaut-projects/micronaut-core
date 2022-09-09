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
package io.micronaut.context.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The visitor adds Validated annotation if one of the parameters is a constraint or @Valid.
 *
 * @author Denis Stepanov
 * @since 3.7.0
 */
@Internal
public class ValidationVisitor implements TypeElementVisitor<Object, Object> {

    private static final String ANN_CONSTRAINT = "javax.validation.Constraint";
    private static final String ANN_VALID = "javax.validation.Valid";
    private static final String ANN_VALIDATED = "io.micronaut.validation.Validated";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return new HashSet<>(Arrays.asList(ANN_CONSTRAINT, ANN_VALIDATED));
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.isAnnotationPresent(ANN_VALIDATED)) {
            return;
        }
        if (Arrays.stream(element.getParameters()).anyMatch(p -> p.hasStereotype(ANN_VALID) || p.hasStereotype(ANN_CONSTRAINT))) {
            if (element.isPrivate()) {
                context.fail("Method annotated with constraints but is declared private. Change the method to be non-private in order for AOP advice to be applied.", element);
                return;
            }
            element.annotate(ANN_VALIDATED);
        }
    }
}
