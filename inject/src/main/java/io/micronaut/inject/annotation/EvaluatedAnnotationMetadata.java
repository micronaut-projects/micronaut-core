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
import io.micronaut.context.BeanContextConfigurable;
import io.micronaut.context.expressions.ConfigurableExpressionEvaluationContext;
import io.micronaut.context.expressions.DefaultExpressionEvaluationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;

import java.lang.annotation.Annotation;

/**
 * Variation of {@link AnnotationMetadata} that is used when evaluated expression
 * in annotation values need to be resolved at runtime.
 *
 * @author Sergey Gavrilov
 * @since 4.0
 */
@Experimental
public final class EvaluatedAnnotationMetadata extends MappingAnnotationMetadataDelegate implements BeanContextConfigurable, BeanDefinitionAware {

    private final AnnotationMetadata delegateAnnotationMetadata;

    private ConfigurableExpressionEvaluationContext evaluationContext;

    private EvaluatedAnnotationMetadata(AnnotationMetadata targetMetadata,
                                        ConfigurableExpressionEvaluationContext evaluationContext) {
        this.delegateAnnotationMetadata = targetMetadata;
        this.evaluationContext = evaluationContext;
    }

    /**
     * @return The evaluation context.
     * @since 4.1.0
     */
    public @NonNull ConfigurableExpressionEvaluationContext getEvaluationContext() {
        return evaluationContext;
    }

    /**
     * Provide a copy of this annotation metadata with passed method arguments.
     *
     * @param thisObject The object that represent this object
     * @param args arguments passed to method
     * @return copy of annotation metadata
     */
    public EvaluatedAnnotationMetadata withArguments(@Nullable Object thisObject, Object[] args) {
        return new EvaluatedAnnotationMetadata(
            delegateAnnotationMetadata,
            evaluationContext.withArguments(thisObject, args)
        );
    }

    @Override
    public void configure(BeanContext context) {
        evaluationContext = evaluationContext.withBeanContext(context);
    }

    @Override
    public void setBeanDefinition(BeanDefinition<?> beanDefinition) {
        evaluationContext = evaluationContext.withOwningBean(beanDefinition);
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
        return new EvaluatedAnnotationValue<>(av, evaluationContext);
    }
}
