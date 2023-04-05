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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.expressions.ExpressionEvaluationContext;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Version of {@link ConvertibleValuesMap} that is aware of evaluated expressions.
 *
 * @param <V> The generic value
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
final class EvaluatedConvertibleValuesMap<V> implements ConvertibleValues<V> {

    private final ExpressionEvaluationContext evaluationContext;
    private final ConvertibleValues<V> delegateValues;

    EvaluatedConvertibleValuesMap(ExpressionEvaluationContext evaluationContext,
                                  ConvertibleValues<V> delegateValues) {
        this.evaluationContext = evaluationContext;
        this.delegateValues = delegateValues;
    }

    @Override
    public Set<String> names() {
        return delegateValues.names();
    }

    @Override
    public <T> Optional<T> get(CharSequence name,
                               ArgumentConversionContext<T> conversionContext) {
        V value = delegateValues.getValue(name);
        if (value instanceof EvaluatedExpression expression) {
            if (EvaluatedExpression.class.isAssignableFrom(conversionContext.getArgument().getClass())) {
                return Optional.of((T) value);
            }

            Object evaluationResult = expression.evaluate(evaluationContext);
            if (evaluationResult == null ||  conversionContext.getArgument().isAssignableFrom(evaluationResult.getClass())) {
                return Optional.ofNullable((T) evaluationResult);
            }
            return ConversionService.SHARED.convert(evaluationResult, conversionContext);
        } else {
            return delegateValues.get(name, conversionContext);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<V> values() {
        return delegateValues.values().stream().map(v -> {
            if (v instanceof EvaluatedExpression expression) {
                Object evaluationResult = expression.evaluate(evaluationContext);
                return (V) evaluationResult;
            }
            return v;
        }).collect(Collectors.toList());
    }
}
