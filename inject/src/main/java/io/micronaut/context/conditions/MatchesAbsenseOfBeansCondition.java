/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context.conditions;

import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import java.util.Collection;

/**
 * Missing beans condition.
 *
 * @param missingBeans The missing beans
 * @author Denis Stepanov
 * @since 4.6
 */
@UsedByGeneratedCode
@Internal
public record MatchesAbsenseOfBeansCondition(AnnotationClassValue<?>[] missingBeans) implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        if (component instanceof BeanDefinition<?> bd) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context.getBeanContext();

            for (AnnotationClassValue<?> bean : missingBeans) {
                Class<?> type = bean.getType().orElse(null);
                if (type == null) {
                    continue;
                }
                // remove self by passing definition as filter
                final Collection<? extends BeanDefinition<?>> beanDefinitions = beanContext.findBeanCandidates(
                    context.getBeanResolutionContext(),
                    Argument.of(type),
                    bd
                );
                for (BeanDefinition<?> beanDefinition : beanDefinitions) {
                    if (!beanDefinition.isAbstract()) {
                        context.fail("Existing bean [" + beanDefinition.getName() + "] of type [" + type.getName() + "] registered in context");
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
