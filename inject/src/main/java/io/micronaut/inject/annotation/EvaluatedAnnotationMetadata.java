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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDefinitionAware;
import io.micronaut.context.ContextConfigurable;
import io.micronaut.context.expressions.ConfigurableExpressionEvaluationContext;
import io.micronaut.context.expressions.DefaultExpressionEvaluationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.inject.BeanDefinition;

import java.lang.annotation.Annotation;

/**
 * Variation of {@link AnnotationMetadata} that is used when evaluated expression
 * in annotation values need to be resolved at runtime.
 *
 * @author Sergey Gavrilov
 * @since 4.0
 */
@Internal
public final class EvaluatedAnnotationMetadata extends MappingAnnotationMetadataDelegate implements ContextConfigurable, BeanDefinitionAware {

    private final AnnotationMetadata delegateAnnotationMetadata;

    private ConfigurableExpressionEvaluationContext evaluationContext;

    private EvaluatedAnnotationMetadata(AnnotationMetadata targetMetadata,
                                        ConfigurableExpressionEvaluationContext evaluationContext) {
        this.delegateAnnotationMetadata = targetMetadata;
        this.evaluationContext = evaluationContext;
    }

    /**
     * Provide a copy of this annotation metadata with passed method arguments.
     *
     * @param args arguments passed to method
     * @return copy of annotation metadata
     */
    public EvaluatedAnnotationMetadata copyWithArgs(Object[] args) {
        return new EvaluatedAnnotationMetadata(
            delegateAnnotationMetadata,
            evaluationContext.setArguments(args));
    }

    @Override
    public void configure(BeanContext context) {
        evaluationContext = evaluationContext.setBeanContext(context);
    }

    @Override
    public void setBeanDefinition(BeanDefinition<?> beanDefinition) {
        evaluationContext = evaluationContext.setOwningBean(beanDefinition);
    }

    public static AnnotationMetadata wrapIfNecessary(AnnotationMetadata targetMetadata) {
        if (targetMetadata == null) {
            return null;
        } else if (targetMetadata instanceof EvaluatedAnnotationMetadata) {
            return targetMetadata;
        } else if (targetMetadata.hasEvaluatedExpressions()) {
            return new EvaluatedAnnotationMetadata(targetMetadata, new DefaultExpressionEvaluationContext());
        }
        return targetMetadata;
    }

    @Override
    public boolean hasEvaluatedExpressions() {
        // this type of metadata always has evaluated expressions
        return true;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return delegateAnnotationMetadata;
    }

    @Override
    public <T extends Annotation> AnnotationValue<T> mapAnnotationValue(AnnotationValue<T> av) {
        return new AnnotationValue<T>(
            av,
            AnnotationMetadataSupport.getDefaultValues(av.getAnnotationName()),
            new EvaluatedConvertibleValuesMap<>(evaluationContext, av.getConvertibleValues()),
            value -> {
                if (value instanceof EvaluatedExpression expression) {
                    return expression.evaluate(evaluationContext);
                }
                return value;
            });
    }
}
