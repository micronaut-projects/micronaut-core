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
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.StatementDef;

import java.util.List;

/**
 * Context class used for compiling expressions.
 *
 * @param evaluationVisitorContext evaluation visitor context
 * @param expressionEvaluationContextVar The context variable
 * @param additionalStatements The additional statements
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public record ExpressionCompilationContext(@NonNull ExpressionVisitorContext evaluationVisitorContext,
                                           @NonNull ExpressionDef expressionEvaluationContextVar,
                                           @NonNull List<StatementDef> additionalStatements) {

    /**
     * @return The visitor context
     */
    public VisitorContext visitorContext() {
        return evaluationVisitorContext.visitorContext();
    }
}
