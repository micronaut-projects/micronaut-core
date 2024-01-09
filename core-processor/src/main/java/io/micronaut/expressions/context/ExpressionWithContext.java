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
package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.expressions.EvaluatedExpressionReference;

import java.util.Objects;

/**
 * Metadata for evaluated expression used at compilation time
 * to generate expression class.
 *
 * @param expressionReference reference to evaluated expression in annotation
 * @param evaluationContext the context against which expression will be evaluated
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
public record ExpressionWithContext(@NonNull EvaluatedExpressionReference expressionReference,
                                    @NonNull ExpressionEvaluationContext evaluationContext) {

    /**
     * Provides initial annotation value treated as evaluated expression.
     *
     * @return initial annotation value
     */
    @NonNull
    public Object annotationValue() {
        return expressionReference.annotationValue();
    }

    /**
     * Provides generated class name for this expression.
     *
     * @return expression class name
     */
    @NonNull
    public String expressionClassName() {
        return expressionReference.expressionClassName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExpressionWithContext that = (ExpressionWithContext) o;
        return Objects.equals(expressionReference, that.expressionReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expressionReference);
    }
}
