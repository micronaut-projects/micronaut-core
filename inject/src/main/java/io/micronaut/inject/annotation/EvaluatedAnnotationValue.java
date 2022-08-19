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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.context.ContextConfigurable;
import io.micronaut.core.expression.EvaluatedExpression;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link AnnotationValue} which is evaluated against BeanContext.
 *
 * @author graemerocher
 * @since 1.0
 * @param <A> The annotation type
 */
@Internal
public class EvaluatedAnnotationValue<A extends Annotation> extends AnnotationValue<A> {

    EvaluatedAnnotationValue(@Nullable BeanContext beanContext,
                             @Nullable BeanDefinition<?> owningBean,
                             Object[] args,
                             AnnotationValue<A> target) {
        super(
            target,
            AnnotationMetadataSupport.getDefaultValues(target.getAnnotationName()),
            new EvaluatedConvertibleValuesMap<>(beanContext, owningBean, args, target.getConvertibleValues()),
            value -> {
                if (value instanceof EvaluatedExpression expression) {
                    if (value instanceof ContextConfigurable ctxConfigurable && beanContext != null) {
                        ctxConfigurable.configure(beanContext);
                        if (value instanceof BeanDefinitionAware beanDefinitionAware) {
                            beanDefinitionAware.setBeanDefinition(owningBean);
                        }
                    }
                    return expression.evaluate(args);
                }
                return value;
            });
    }

    @Override
    public <T> Optional<T> get(CharSequence member, ArgumentConversionContext<T> conversionContext) {
        Optional<T> value = getConvertibleValues().get(member, conversionContext);
        if (value.isPresent()) {
            return value;
        }

        return super.get(member, conversionContext);
    }

    @Override
    public boolean hasEvaluatedExpressions() {
        return true;
    }
}
