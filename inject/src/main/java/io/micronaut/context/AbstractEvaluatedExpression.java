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
package io.micronaut.context;

import io.micronaut.context.exceptions.ExpressionEvaluationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.expression.EvaluatedExpression;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

/**
 * Default implementation for evaluated expressions. This class is subclassed
 * by evaluated expressions classes at compilation time.
 *
 * @author Sergey Gavrilov
 * @since 4.0.0
 */
@Internal
@UsedByGeneratedCode
public abstract class AbstractEvaluatedExpression implements EvaluatedExpression,
                                                             ContextConfigurable,
                                                             BeanDefinitionAware {
    private final Object initialAnnotationValue;

    private BeanContext beanContext;
    private BeanResolutionContext resolutionContext;
    private BeanDefinition<?> owningBean;

    public AbstractEvaluatedExpression(Object initialAnnotationValue) {
        this.initialAnnotationValue = initialAnnotationValue;
    }

    @Override
    public Object getInitialAnnotationValue() {
        return initialAnnotationValue;
    }

    @Override
    public void setBeanDefinition(BeanDefinition<?> beanDefinition) {
        this.owningBean = beanDefinition;
    }

    @Override
    public void configure(BeanContext context) {
        this.beanContext = context;
    }

    @Override
    public final Object evaluate(Object... args) {
        try {
            return doEvaluate(args);
        } catch (Throwable ex) {
            throw new ExpressionEvaluationException(ex);
        } finally {
            if (resolutionContext != null) {
                resolutionContext = null;
            }
        }
    }

    /**
     * This method is overridden by expression classes generated at compilation time and
     * contains concrete expression evaluation logic.
     *
     * @param args Array of arguments which need to be passed to expression
     *             for evaluation. Args are used when expression itself is used
     *             on method and references method arguments
     * @return evaluation result
     */
    protected Object doEvaluate(Object... args) {
        return initialAnnotationValue;
    }

    protected final <T> T getBean(Class<T> type) {
        if (beanContext == null) {
            throw new ExpressionEvaluationException(
                "Can not evaluate expression [" + getInitialAnnotationValue() + "]. " +
                    "Can not obtain bean of type [" + type + "] since bean context is not set");
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
    public String toString() {
        return initialAnnotationValue.toString();
    }
}
