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
package io.micronaut.inject.annotation;

import io.micronaut.context.expressions.ConfigurableExpressionEvaluationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.EvaluatedExpression;

import java.lang.annotation.Annotation;
import java.util.function.Function;

/**
 * An EvaluatedAnnotationValue is a {@link AnnotationValue} that contains one or more expressions.
 *
 * @param <A> The annotation
 * @since 4.0.0
 */
@Experimental
public final class EvaluatedAnnotationValue<A extends Annotation> extends AnnotationValue<A> {
    private final ConfigurableExpressionEvaluationContext evaluationContext;
    private final AnnotationValue<A> annotationValue;

    EvaluatedAnnotationValue(AnnotationValue<A> annotationValue, ConfigurableExpressionEvaluationContext evaluationContext) {
        super(
            annotationValue,
            annotationValue.getDefaultValues(),
            new EvaluatedConvertibleValuesMap<>(evaluationContext, annotationValue.getConvertibleValues()),
            value -> {
                if (value instanceof EvaluatedExpression expression) {
                    return expression.evaluate(evaluationContext);
                } else if (annotationValue instanceof EnvironmentAnnotationValue<A> eav) {
                    Function<Object, Object> valueMapper = eav.getValueMapper();
                    if (valueMapper != null) {
                        return valueMapper.apply(value);
                    }
                }
                return value;
            }
        );
        this.evaluationContext = evaluationContext;
        this.annotationValue = annotationValue;
    }

    /**
     * Provide a copy of this annotation metadata with passed method arguments.
     *
     * @param thisObject The object that represents this in a non-static context.
     * @param args arguments passed to method
     * @return copy of annotation metadata
     */
    public EvaluatedAnnotationValue<A> withArguments(@Nullable Object thisObject, Object[] args) {
        return new EvaluatedAnnotationValue<>(
            annotationValue,
            evaluationContext.withArguments(thisObject, args));
    }
}
