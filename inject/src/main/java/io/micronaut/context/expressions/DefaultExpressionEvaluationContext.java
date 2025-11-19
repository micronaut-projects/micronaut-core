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

import io.micronaut.context.AbstractBeanResolutionContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.exceptions.ExpressionEvaluationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

/**
 * Default implementation of {@link ConfigurableExpressionEvaluationContext}.
 * For this implementation, the methods mutating evaluation context return new instance of
 * expression evaluation context.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public final class DefaultExpressionEvaluationContext implements ConfigurableExpressionEvaluationContext {

    private final Object thisObject;
    private final Object[] args;
    private final BeanContext beanContext;
    private final BeanDefinition<?> owningBean;

    private BeanResolutionContext resolutionContext;

    public DefaultExpressionEvaluationContext() {
        this(null, null, null, null);
    }

    public DefaultExpressionEvaluationContext(@Nullable Object thisObject, @Nullable Object[] args,
                                              @Nullable BeanContext beanContext,
                                              @Nullable BeanDefinition<?> owningBean) {
        this.thisObject = thisObject;
        this.args = args;
        this.beanContext = beanContext;
        this.owningBean = owningBean;
    }

    @Override
    public ConfigurableExpressionEvaluationContext withArguments(Object thisObject, Object[] args) {
        DefaultExpressionEvaluationContext evaluationContext = new DefaultExpressionEvaluationContext(
            thisObject, args,
            this.beanContext,
            this.owningBean
        );
        evaluationContext.resolutionContext = resolutionContext;
        return evaluationContext;
    }

    @Override
    public ConfigurableExpressionEvaluationContext withOwningBean(BeanDefinition<?> beanDefinition) {
        DefaultExpressionEvaluationContext evaluationContext = new DefaultExpressionEvaluationContext(
            thisObject, this.args,
            this.beanContext,
            beanDefinition
        );
        evaluationContext.resolutionContext = resolutionContext;
        return evaluationContext;
    }

    @Override
    public ConfigurableExpressionEvaluationContext withBeanContext(BeanContext beanContext) {
        DefaultExpressionEvaluationContext evaluationContext = new DefaultExpressionEvaluationContext(
            thisObject, this.args,
            beanContext,
            this.owningBean
        );
        evaluationContext.resolutionContext = resolutionContext;
        return evaluationContext;
    }

    @Override
    public Object getThis() {
        if (thisObject == null) {
            throw new ExpressionEvaluationException("Current resolve 'this' within expression context. Expressions that resolve 'this' should be executed in a non-static context.");
        }
        return thisObject;
    }

    @Override
    public Object getArgument(int index) {
        if (args == null || args.length == 0 || args.length < index) {
            throw new ExpressionEvaluationException(
                "Can not obtain argument at index [" + index + "] since arguments are not provided");
        }

        return args[index];
    }

    @Override
    public String getProperty(String name) {
        if (beanContext == null || !(beanContext instanceof ApplicationContext applicationContext)) {
            throw new ExpressionEvaluationException("Can not obtain environment property [" + name + "] " +
                                                        "since application context is not set");
        }

        return applicationContext.getProperty(name, String.class)
                   .orElse(null);
    }

    @Override
    public <T> T getBean(Class<T> type) {
        if (beanContext == null) {
            throw new ExpressionEvaluationException("Can not obtain bean of type [" + type + "] since bean context is not set");
        }
        if (resolutionContext == null && owningBean != null) {
            resolutionContext = new DefaultBeanResolutionContext(beanContext, owningBean);
        }
        if (resolutionContext instanceof AbstractBeanResolutionContext abstractBeanResolutionContext) {
            BeanIdentifier identifier = BeanIdentifier.of(type.getName());
            BeanRegistration<Object> existing = resolutionContext.getInFlightBean(identifier);
            if (existing != null) {
                return (T) existing.getBean();
            } else {
                Argument<T> t = Argument.of(type);
                try (BeanResolutionContext.Path ignored =
                         resolutionContext.getPath().pushAnnotationResolve(owningBean, t)) {
                    BeanRegistration<T> beanRegistration = abstractBeanResolutionContext.getBeanRegistration(t, null);
                    resolutionContext.addInFlightBean(identifier, beanRegistration);
                    return beanRegistration.getBean();
                }
            }
        }

        return beanContext.getBean(type);
    }

    @Override
    public void close() throws Exception {
        if (resolutionContext != null) {
            resolutionContext.close();
            resolutionContext = null;
        }
    }
}
