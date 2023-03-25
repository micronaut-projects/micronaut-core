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
package io.micronaut.context.expressions;

import io.micronaut.context.exceptions.ExpressionEvaluationException;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;

/**
 * Default implementation for evaluated expressions. This class is subclassed
 * by evaluated expressions classes at compilation time.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
@UsedByGeneratedCode
public abstract class AbstractEvaluatedExpression implements EvaluatedExpression {

    private final Object initialAnnotationValue;

    public AbstractEvaluatedExpression(Object initialAnnotationValue) {
        this.initialAnnotationValue = initialAnnotationValue;
    }

    @Override
    public final Object evaluate(ExpressionEvaluationContext evaluationContext) {
        try (evaluationContext) {
            return doEvaluate(evaluationContext);
        } catch (Throwable ex) {
            throw new ExpressionEvaluationException(
                "Can not evaluate expression [" + initialAnnotationValue + "]. " + ex.getMessage(), ex);
        }
    }

    /**
     * This method is overridden by expression classes generated at compilation time and
     * contains concrete expression evaluation logic.
     *
     * @param evaluationContext context used for expression evaluation
     * @return evaluation result
     */
    protected Object doEvaluate(ExpressionEvaluationContext evaluationContext) {
        return initialAnnotationValue;
    }

    @Override
    public String toString() {
        return initialAnnotationValue.toString();
    }
}
