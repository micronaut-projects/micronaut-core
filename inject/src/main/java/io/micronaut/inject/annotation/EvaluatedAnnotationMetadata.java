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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
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

    private BeanContext beanContext;
    private BeanDefinition<?> owningBean;

    private final AnnotationMetadata delegateAnnotationMetadata;
    private final Object[] args;

    private EvaluatedAnnotationMetadata(AnnotationMetadata targetMetadata, Object[] args) {
        this.delegateAnnotationMetadata = targetMetadata;
        this.args = args;
    }

    private EvaluatedAnnotationMetadata(AnnotationMetadata targetMetadata) {
        this(targetMetadata, new Object[0]);
    }

    public static AnnotationMetadata wrapIfNecessary(AnnotationMetadata targetMetadata,
                                                     Object[] args) {
        if (targetMetadata == null) {
            return null;
        } else if (targetMetadata instanceof EvaluatedAnnotationMetadata eam) {
            AnnotationMetadata delegateMetadata = eam.getAnnotationMetadata();
            EvaluatedAnnotationMetadata methodInvocationMetadata =
                new EvaluatedAnnotationMetadata(delegateMetadata, args);
            methodInvocationMetadata.setBeanDefinition(eam.owningBean);
            methodInvocationMetadata.configure(eam.beanContext);
            return methodInvocationMetadata;
        }

        if (targetMetadata.hasEvaluatedExpressions()) {
            return new EvaluatedAnnotationMetadata(targetMetadata);
        }
        return targetMetadata;
    }

    public static AnnotationMetadata wrapIfNecessary(AnnotationMetadata targetMetadata) {
        return wrapIfNecessary(targetMetadata, new Object[0]);
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
        return new EvaluatedAnnotationValue<>(beanContext, owningBean, args, av);
    }

    @Override
    public void configure(BeanContext context) {
        this.beanContext = context;
    }

    @Override
    public void setBeanDefinition(BeanDefinition<?> beanDefinition) {
        this.owningBean = beanDefinition;
    }
}
