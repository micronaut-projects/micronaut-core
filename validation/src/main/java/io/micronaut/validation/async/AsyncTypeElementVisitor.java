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
package io.micronaut.validation.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.scheduling.annotation.Async;

import java.util.concurrent.CompletionStage;

/**
 * A {@link TypeElementVisitor} that validates methods annotated with {@link Async} return void or futures.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class AsyncTypeElementVisitor implements TypeElementVisitor<Object, Async> {

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        ClassElement returnType = element.getReturnType();
        boolean isValid = returnType != null &&
                (returnType.isAssignable(CompletionStage.class) || returnType.isAssignable(void.class) ||
                        Publishers.getKnownReactiveTypes().stream().anyMatch(returnType::isAssignable));

        if (!isValid) {
            context.fail("Method must return void or a subtype of CompletionStage", element);
        }
    }
}
