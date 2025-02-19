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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.EvaluatedExpression;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member, Class<T> type) {
        return super.getAnnotations(member, type).stream().map(av -> new EvaluatedAnnotationValue<>(av, evaluationContext)).collect(Collectors.toList());
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member) {
        return super.<T>getAnnotations(member)
            .stream()
            .map(av -> new EvaluatedAnnotationValue<>(av, evaluationContext))
            .collect(Collectors.toList());
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> getAnnotation(String member, Class<T> type) {
        return super.getAnnotation(member, type).map(av -> new EvaluatedAnnotationValue<>(av, evaluationContext));
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> getAnnotation(@NonNull String member) {
        return super.<T>getAnnotation(member).map(av -> new EvaluatedAnnotationValue<>(av, evaluationContext));
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
