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
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.exceptions.ExpressionEvaluationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

/**
 * Default implementation of {@link ConfigurableExpressionEvaluationContext}.
 * For this implementation, the methods mutating evaluation context return new instance of
 * expression evaluation context.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public class DefaultExpressionEvaluationContext implements ConfigurableExpressionEvaluationContext {

    private final Object[] args;
    private final BeanContext beanContext;
    private final BeanDefinition<?> owningBean;

    private BeanResolutionContext resolutionContext;

    public DefaultExpressionEvaluationContext() {
        this(null, null, null);
    }

    public DefaultExpressionEvaluationContext(@Nullable Object[] args,
                                              @Nullable BeanContext beanContext,
                                              @Nullable BeanDefinition<?> owningBean) {
        this.args = args;
        this.beanContext = beanContext;
        this.owningBean = owningBean;
    }

    @Override
    public ConfigurableExpressionEvaluationContext setArguments(Object[] args) {
        return new DefaultExpressionEvaluationContext(args, this.beanContext, this.owningBean);
    }

    @Override
    public ConfigurableExpressionEvaluationContext setOwningBean(BeanDefinition<?> beanDefinition) {
        return new DefaultExpressionEvaluationContext(this.args, this.beanContext, beanDefinition);
    }

    @Override
    public ConfigurableExpressionEvaluationContext setBeanContext(BeanContext beanContext) {
        return new DefaultExpressionEvaluationContext(this.args, beanContext, this.owningBean);
    }

    @Override
    public Object getArgument(int index) {
        if (args == null || args.length == 0) {
            throw new ExpressionEvaluationException(
                "Can not obtain argument at index [" + index + "] since arguments are not provided");
        }

        return args[index];
    }

    @Override
    public <T> T getBean(Class<T> type) {
        if (beanContext == null) {
            throw new ExpressionEvaluationException("Can not obtain bean of type [" + type + "] since bean context is not set");
        }

        if (beanContext instanceof DefaultBeanContext defaultBeanContext) {
            if (resolutionContext == null && owningBean != null) {
                resolutionContext = new DefaultBeanResolutionContext(defaultBeanContext, owningBean);
            }

            if (resolutionContext != null) {
                try (BeanResolutionContext.Path ignored =
                         resolutionContext.getPath().pushAnnotationResolve(owningBean, Argument.of(type))) {
                    return defaultBeanContext.getBean(resolutionContext, type);
                }
            }
        }

        return beanContext.getBean(type);
    }

    @Override
    public void close() throws Exception {
        resolutionContext = null;
    }
}
