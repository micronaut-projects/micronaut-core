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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
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
    private final ConversionService conversionService;
    private final Map<ExecutableMethod<?, ?>, MapInvocation> cachedInvocations = new ConcurrentHashMap<>();

    MapperIntroduction(ConversionService conversionService) {
        this.conversionService = conversionService;
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
                Class<Object> fromClass = fromType.getType();
                boolean isMap = Map.class.isAssignableFrom(fromClass);
                BeanIntrospection<Object> fromIntrospection = isMap ? null : BeanIntrospection.getIntrospection(fromClass);
                AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
                Mapper.MapStrategy.ConflictStrategy conflictStrategy = annotationMetadata.enumValue(Mapper.class, "conflictStrategy", Mapper.MapStrategy.ConflictStrategy.class)
                    .orElse(Mapper.MapStrategy.ConflictStrategy.CONVERT);
                Mapper.MapStrategy.Builder builder = Mapper.MapStrategy.builder().withConflictStrategy(conflictStrategy);

                if (annotationMetadata.isPresent(Mapper.class, AnnotationMetadata.VALUE_MEMBER)) {
                    List<AnnotationValue<Mapper.Mapping>> annotations = context.getAnnotationValuesByType(Mapper.Mapping.class);

                    Map<String, Function<Object, BiFunction<Mapper.MapStrategy, Object, Object>>> customMappers = buildCustomMappers(fromIntrospection, annotations, isMap);

                    // requires runtime evaluation
                    if (annotationMetadata instanceof EvaluatedAnnotationMetadata) {
                        if (isMap) {
                            invocation = callContext -> {
                                Mapper.MapStrategy mapStrategy = buildMapStrategy(conflictStrategy, customMappers, callContext);
                                return map(
                                    (Map<String, Object>) callContext.getParameterValues()[0],
                                    mapStrategy,
                                    toIntrospection
                                );
                            };
                        } else {
                            invocation = callContext -> {
                                Mapper.MapStrategy mapStrategy = buildMapStrategy(conflictStrategy, customMappers, callContext);
                                return map(
                                    callContext.getParameterValues()[0],
                                    mapStrategy,
                                    fromIntrospection,
                                    toIntrospection
                                );
                            };
                        }
                    } else if (!customMappers.isEmpty()) {
                        invocation = callContext -> {
                            Mapper.MapStrategy mapStrategy = buildMapStrategy(conflictStrategy, customMappers, callContext);
                            Object input = callContext.getParameterValues()[0];
                            return map(
                                input,
                                mapStrategy,
                                fromIntrospection,
                                toIntrospection
                            );
                        };
                    } else {
                        invocation = mapDefault(toIntrospection, fromIntrospection, builder, isMap);
                    }
                } else {
                    invocation = mapDefault(toIntrospection, fromIntrospection, builder, isMap);
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

    private static Mapper.MapStrategy buildMapStrategy(Mapper.MapStrategy.ConflictStrategy conflictStrategy, Map<String, Function<Object, BiFunction<Mapper.MapStrategy, Object, Object>>> customMappers, MethodInvocationContext<Object, Object> callContext) {
        Mapper.MapStrategy.Builder callbuilder = Mapper.MapStrategy.builder().withConflictStrategy(conflictStrategy);
        AnnotationMetadata callAnnotationMetadata = callContext.getAnnotationMetadata();
        if (callAnnotationMetadata instanceof EvaluatedAnnotationMetadata evaluatedAnnotationMetadata) {
            ConfigurableExpressionEvaluationContext evaluationContext = evaluatedAnnotationMetadata.getEvaluationContext();
            customMappers.forEach((name, mapperSupplier) -> callbuilder.withCustomMapper(name, mapperSupplier.apply(evaluationContext)));
        } else {
            customMappers.forEach((name, mapperSupplier) -> callbuilder.withCustomMapper(name, mapperSupplier.apply(null)));
        }

        return callbuilder.build();
    }

    private static Map<String, Function<Object, BiFunction<Mapper.MapStrategy, Object, Object>>> buildCustomMappers(
        BeanIntrospection<Object> fromIntrospection,
        List<AnnotationValue<Mapper.Mapping>> annotations,
        boolean isMap) {
        Map<String, Function<Object, BiFunction<Mapper.MapStrategy, Object, Object>>> customMappers = new HashMap<>();
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
                    String propertyName = from.toString();
                    if (fromIntrospection != null) {
                        BeanProperty<Object, Object> fromProperty = fromIntrospection.getRequiredProperty(propertyName, Object.class);
                        customMappers.put(to, (expressionEvaluationContext -> (mapStrategy, object) -> fromProperty.get(object)));
                    } else if (isMap) {
                        customMappers.put(to, (expressionEvaluationContext -> (mapStrategy, object) -> ((Map<String, Object>) object).get(propertyName)));
                    }
                }

            }
        }
        return customMappers;
    }

    private MapInvocation mapDefault(BeanIntrospection<Object> toIntrospection, BeanIntrospection<Object> fromIntrospection, Mapper.MapStrategy.Builder builder, boolean isMap) {
        MapInvocation invocation;
        Mapper.MapStrategy mapStrategy = builder.build();
        if (isMap) {
            invocation = callContext -> map(
                (Map<String, Object>) callContext.getParameterValues()[0],
                mapStrategy,
                toIntrospection
            );
        } else {
            invocation = callContext -> map(
                callContext.getParameterValues()[0],
                mapStrategy,
                fromIntrospection,
                toIntrospection
            );
        }
        return invocation;
    }

    @FunctionalInterface
    private interface MapInvocation {
        Object map(MethodInvocationContext<Object, Object> invocationContext);
    }

    private  <I, O> O map(I input, Mapper.MapStrategy mapStrategy, BeanIntrospection<I> inputIntrospection, BeanIntrospection<O> outputIntrospection) {
        boolean isDefault = mapStrategy == Mapper.MapStrategy.DEFAULT;
        Mapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        BeanIntrospection.Builder<O> builder = outputIntrospection.builder();
        @SuppressWarnings("unchecked") @NonNull Argument<Object>[] arguments = (Argument<Object>[]) builder.getArguments();

        if (!isDefault) {
            processCustomMappers(input, mapStrategy, conflictStrategy, builder, arguments);
        }
        for (BeanProperty<I, Object> beanProperty : inputIntrospection.getBeanProperties()) {
            if (!beanProperty.isWriteOnly()) {
                String propertyName = beanProperty.getName();
                if (!isDefault && mapStrategy.customMappers().containsKey(propertyName)) {
                    continue;
                }
                int i = builder.indexOf(propertyName);
                if (i > -1) {
                    Argument<Object> argument = arguments[i];
                    Object value = beanProperty.get(input);
                    if (argument.isInstance(value)) {
                        builder.with(i, argument, value);
                    } else if (conflictStrategy == Mapper.MapStrategy.ConflictStrategy.CONVERT) {
                        builder.convert(i, ConversionContext.of(argument), value, conversionService);
                    } else {
                        builder.with(i, argument, value);
                    }
                }
            }
        }
        return builder.build();
    }

    private <I, O> void processCustomMappers(I input, Mapper.MapStrategy mapStrategy, Mapper.MapStrategy.ConflictStrategy conflictStrategy, BeanIntrospection.Builder<O> builder, @NonNull Argument<Object>[] arguments) {
        mapStrategy.customMappers().forEach((name, func) -> {
            int i = builder.indexOf(name);
            if (i > -1) {
                Argument<Object> argument = arguments[i];
                Object result = func.apply(mapStrategy, input);
                if (argument.isInstance(result)) {
                    builder.with(i, argument, result);
                } else if (conflictStrategy == Mapper.MapStrategy.ConflictStrategy.CONVERT) {
                    builder.convert(i, ConversionContext.of(argument), result, conversionService);
                }
            }
        });
    }

    private <O> O map(Map<String, Object> input, Mapper.MapStrategy mapStrategy, BeanIntrospection<O> outputIntrospection) {
        BeanIntrospection.Builder<O> builder = outputIntrospection.builder();
        @NonNull Argument<Object>[] arguments = (Argument<Object>[]) builder.getArguments();
        handleMapInput(input, mapStrategy, builder, arguments);
        return builder.build();
    }

    private <O> void handleMapInput(Map<String, Object> input, Mapper.MapStrategy mapStrategy, BeanIntrospection.Builder<O> builder, @NonNull Argument<Object>[] arguments) {
        Mapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        boolean isDefault = mapStrategy == Mapper.MapStrategy.DEFAULT;
        if (!isDefault) {
            processCustomMappers(input, mapStrategy, conflictStrategy, builder, arguments);
        }
        input.forEach((key, value) -> {
            int i = builder.indexOf(key);
            if (!isDefault && mapStrategy.customMappers().containsKey(key)) {
                return;
            }
            if (i > -1) {
                Argument<Object> argument = arguments[i];
                if (conflictStrategy == Mapper.MapStrategy.ConflictStrategy.CONVERT) {
                    builder.convert(i, ConversionContext.of(argument), value, conversionService);
                } else {
                    builder.with(i, argument, value);
                }
            }
        });
    }
}
