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
package io.micronaut.validation.executable;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A {@link io.micronaut.inject.visitor.TypeElementVisitor} that validates primitive types can't have a Nullable annotation.
 *
 * @author Iván López
 * @since 2.0.1
 */
@Internal
public class ExecutableVisitor implements TypeElementVisitor<Object, Object> {

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        ParameterElement[] parameters = element.getParameters();

        for (ParameterElement parameter : parameters) {
            if (parameter.getType().isPrimitive() && parameter.isNullable()) {
                context.fail("Primitive types can not be null", parameter);
            }
        }
    }
}
