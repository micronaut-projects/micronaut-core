/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.expressions.parser.compilation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.expressions.context.ExpressionCompilationContext;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Context class used for compiling expressions.
 *
 * @param compilationContext expression compilation context
 * @param visitorContext     visitor context
 * @param methodVisitor      method visitor for compiled expression class
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public record ExpressionVisitorContext(@NonNull ExpressionCompilationContext compilationContext,
                                       @NonNull VisitorContext visitorContext,
                                       @NonNull GeneratorAdapter methodVisitor) {
}
