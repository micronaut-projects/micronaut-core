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

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.inject.BeanDefinition;

/**
 * Expression evaluation context that can be configured before evaluation.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public sealed interface ConfigurableExpressionEvaluationContext extends ExpressionEvaluationContext permits DefaultExpressionEvaluationContext {

    /**
     * Set arguments passed to invoked method.
     *
     * @param args method arguments
     * @return evaluation context which arguments can be used in evaluation.
     */
    @NonNull
    default ConfigurableExpressionEvaluationContext withArguments(@Nullable Object[] args) {
        return withArguments(null, args);
    }

    /**
     * Set arguments passed to invoked method.
     *
     * @param thisObject In the case of non-static methods the object that represents this object
     * @param args method arguments
     * @return evaluation context which arguments can be used in evaluation.
     */
    @NonNull
    ConfigurableExpressionEvaluationContext withArguments(@Nullable Object thisObject, @Nullable Object[] args);

    /**
     * Set bean owning evaluated expression.
     *
     * @param beanDefinition owning bean definition
     * @return evaluation context aware of owning bean.
     */
    @NonNull
    ConfigurableExpressionEvaluationContext withOwningBean(@Nullable BeanDefinition<?> beanDefinition);

    /**
     * Set context in which expression is evaluated.
     *
     * @param beanContext bean context
     * @return evaluation context aware of bean context.
     */
    @NonNull
    ConfigurableExpressionEvaluationContext withBeanContext(@Nullable BeanContext beanContext);
}
