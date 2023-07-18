/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.runtime.beans;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Mapper;
import io.micronaut.context.expressions.ConfigurableExpressionEvaluationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMapper;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Introduction advice for {@link Mapper}.
 */
@InterceptorBean(Mapper.class)
@Internal
@BootstrapContextCompatible
final class MapperIntroduction implements MethodInterceptor<Object, Object> {
    private final IntrospectionBeanMapHandler beanMapHandler;
    private final Map<ExecutableMethod<?, ?>, MapInvocation> cachedInvocations = new ConcurrentHashMap<>();

    MapperIntroduction(IntrospectionBeanMapHandler beanMapHandler) {
        this.beanMapHandler = beanMapHandler;
    }

    @Override
    public int getOrder() {
        return -100; // higher precedence
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.hasDeclaredAnnotation(Mapper.class)) {
            ExecutableMethod<Object, Object> key = context.getExecutableMethod();
            MapInvocation invocation = cachedInvocations.get(key);
            if (invocation == null) {
                Argument<Object> toType = context.getReturnType().asArgument();
                // should never be empty, validated at compile time
                Argument<Object> fromType = (Argument<Object>) context.getArguments()[0];
                BeanIntrospection<Object> toIntrospection = BeanIntrospection.getIntrospection(toType.getType());
                BeanIntrospection<Object> fromIntrospection = BeanIntrospection.getIntrospection(fromType.getType());
                AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
                BeanMapper.MapStrategy.ConflictStrategy conflictStrategy = annotationMetadata.enumValue(Mapper.class, "conflictStrategy", BeanMapper.MapStrategy.ConflictStrategy.class)
                    .orElse(BeanMapper.MapStrategy.ConflictStrategy.CONVERT);
                BeanMapper.MapStrategy.Builder builder = BeanMapper.MapStrategy.builder().withConflictStrategy(conflictStrategy);

                if (annotationMetadata.isPresent(Mapper.class, AnnotationMetadata.VALUE_MEMBER)) {
                    List<AnnotationValue<Mapper.Mapping>> annotations = context.getAnnotationValuesByType(Mapper.Mapping.class);

                    Map<String, Function<Object, BiFunction<BeanMapper.MapStrategy, Object, Object>>> customMappers = new HashMap<>();
                    for (AnnotationValue<Mapper.Mapping> mapping : annotations) {
                        String to = mapping.stringValue(Mapper.Mapping.MEMBER_TO).orElse(null);
                        if (StringUtils.isNotEmpty(to)) {
                            Map<CharSequence, Object> values = mapping.getValues();
                            Object from = values.get(Mapper.Mapping.MEMBER_FROM);
                            if (from instanceof EvaluatedExpression evaluatedExpression) {
                                customMappers.put(to, (expressionEvaluationContext ->
                                    (mapStrategy, object) -> evaluatedExpression.evaluate((ExpressionEvaluationContext) expressionEvaluationContext)
                                ));
                            } else if (from != null) {
                                BeanProperty<Object, Object> fromProperty = fromIntrospection.getRequiredProperty(from.toString(), Object.class);
                                customMappers.put(to, (expressionEvaluationContext -> (mapStrategy, object) -> fromProperty.get(object)));
                            }

                        }
                    }

                    // requires runtime evaluation
                    if (annotationMetadata instanceof EvaluatedAnnotationMetadata) {
                        invocation = callContext -> {
                            BeanMapper.MapStrategy.Builder callbuilder = BeanMapper.MapStrategy.builder().withConflictStrategy(conflictStrategy);
                            AnnotationMetadata callAnnotationMetadata = callContext.getAnnotationMetadata();
                            ConfigurableExpressionEvaluationContext evaluationContext = ((EvaluatedAnnotationMetadata) callAnnotationMetadata).getEvaluationContext();
                            customMappers.forEach((name, mapperSupplier) -> callbuilder.withCustomMapper(name, mapperSupplier.apply(evaluationContext)));
                            return beanMapHandler.map(
                                callContext.getParameterValues()[0],
                                callbuilder.build(),
                                fromIntrospection,
                                toIntrospection
                            );
                        };
                    } else if (!customMappers.isEmpty()) {
                        invocation = callContext -> {
                            BeanMapper.MapStrategy.Builder callbuilder = BeanMapper.MapStrategy.builder().withConflictStrategy(conflictStrategy);
                            customMappers.forEach((name, mapperSupplier) -> callbuilder.withCustomMapper(name, mapperSupplier.apply(null)));
                            Object input = callContext.getParameterValues()[0];
                            return beanMapHandler.map(
                                input,
                                callbuilder.build(),
                                fromIntrospection,
                                toIntrospection
                            );
                        };
                    } else {
                        invocation = mapDefault(toIntrospection, fromIntrospection, builder);
                    }
                } else {
                    invocation = mapDefault(toIntrospection, fromIntrospection, builder);
                }

                cachedInvocations.put(key, invocation);
            }

            return invocation.map(
                context
            );
        } else {
            return context.proceed();
        }
    }

    private MapInvocation mapDefault(BeanIntrospection<Object> toIntrospection, BeanIntrospection<Object> fromIntrospection, BeanMapper.MapStrategy.Builder builder) {
        MapInvocation invocation;
        BeanMapper.MapStrategy mapStrategy = builder.build();
        invocation = callContext -> beanMapHandler.map(
            callContext.getParameterValues()[0],
            mapStrategy,
            fromIntrospection,
            toIntrospection
        );
        return invocation;
    }

    @FunctionalInterface
    private interface MapInvocation {
        Object map(MethodInvocationContext<Object, Object> invocationContext);
    }
}
