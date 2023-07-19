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
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.expressions.ExpressionEvaluationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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
                Mapper.ConflictStrategy conflictStrategy = annotationMetadata.enumValue(Mapper.class, "conflictStrategy", Mapper.ConflictStrategy.class)
                    .orElse(null);


                if (annotationMetadata.isPresent(Mapper.class, AnnotationMetadata.VALUE_MEMBER)) {
                    List<AnnotationValue<Mapper.Mapping>> annotations = context.getAnnotationValuesByType(Mapper.Mapping.class);

                    Map<String, Function<Object, BiConsumer<Object, BeanIntrospection.Builder<Object>>>> customMappers = buildCustomMappers(
                        fromIntrospection,
                        toIntrospection,
                        conflictStrategy,
                        annotations,
                        isMap
                    );

                    // requires runtime evaluation
                    if (!customMappers.isEmpty()) {
                        if (isMap) {
                            invocation = callContext -> {
                                MapStrategy mapStrategy = buildMapStrategy(conflictStrategy, customMappers, callContext);
                                return map(
                                    (Map<String, Object>) callContext.getParameterValues()[0],
                                    mapStrategy,
                                    toIntrospection
                                );
                            };
                        } else {
                            invocation = callContext -> {
                                MapStrategy mapStrategy = buildMapStrategy(conflictStrategy, customMappers, callContext);
                                return map(
                                    callContext.getParameterValues()[0],
                                    mapStrategy,
                                    fromIntrospection,
                                    toIntrospection
                                );
                            };
                        }
                    } else {
                        invocation = mapDefault(toIntrospection, fromIntrospection, isMap);
                    }
                } else {
                    invocation = mapDefault(toIntrospection, fromIntrospection, isMap);
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

    private static MapStrategy buildMapStrategy(Mapper.ConflictStrategy conflictStrategy, Map<String, Function<Object, BiConsumer<Object, BeanIntrospection.Builder<Object>>>> customMappers, MethodInvocationContext<Object, Object> callContext) {
        MapStrategy mapStrategy = new MapStrategy(conflictStrategy);
        AnnotationMetadata callAnnotationMetadata = callContext.getAnnotationMetadata();
        if (callAnnotationMetadata instanceof EvaluatedAnnotationMetadata evaluatedAnnotationMetadata) {
            ConfigurableExpressionEvaluationContext evaluationContext = evaluatedAnnotationMetadata.getEvaluationContext();
            customMappers.forEach((name, mapperSupplier) -> mapStrategy.customMappers.put(name, mapperSupplier.apply(evaluationContext)));
        } else {
            customMappers.forEach((name, mapperSupplier) -> mapStrategy.customMappers.put(name, mapperSupplier.apply(null)));
        }

        return mapStrategy;
    }

    private Map<String, Function<Object, BiConsumer<Object, BeanIntrospection.Builder<Object>>>> buildCustomMappers(
        BeanIntrospection<Object> fromIntrospection,
        BeanIntrospection<Object> toIntrospection,
        Mapper.ConflictStrategy conflictStrategy,
        List<AnnotationValue<Mapper.Mapping>> annotations,
        boolean isMap) {
        Map<String, Function<Object, BiConsumer<Object, BeanIntrospection.Builder<Object>>>> customMappers = new HashMap<>();
        for (AnnotationValue<Mapper.Mapping> mapping : annotations) {
            String to = mapping.stringValue(Mapper.Mapping.MEMBER_TO).orElse(null);
            String format = mapping.stringValue(Mapper.Mapping.MEMBER_FORMAT).orElse(null);
            BeanIntrospection.Builder<Object> builderMeta = toIntrospection.builder();


            if (StringUtils.isNotEmpty(to)) {
                int i = builderMeta.indexOf(to);
                if (i == -1) {
                    continue;
                }
                @SuppressWarnings("unchecked") Argument<Object> argument = (Argument<Object>) builderMeta.getArguments()[i];
                ArgumentConversionContext<?> conversionContext = null;
                if (format != null) {
                    conversionContext = ConversionContext.of(argument);
                    MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
                    annotationMetadata.addAnnotation(Format.class.getName(), Map.of(AnnotationMetadata.VALUE_MEMBER, format));
                    conversionContext = conversionContext.with(new AnnotationMetadataHierarchy(argument.getAnnotationMetadata(), annotationMetadata));
                } else if (conflictStrategy == Mapper.ConflictStrategy.CONVERT) {
                    conversionContext = ConversionContext.of(argument);
                }

                Object defaultValue;
                Map<CharSequence, Object> values = mapping.getValues();
                if (mapping.contains(Mapper.Mapping.MEMBER_DEFAULT_VALUE)) {
                    defaultValue = mapping.stringValue(Mapper.Mapping.MEMBER_DEFAULT_VALUE)
                        .flatMap(v -> conversionService.convert(v, argument))
                        .orElseThrow(() -> new IllegalStateException("Invalid defaultValue [" + values.get(Mapper.Mapping.MEMBER_DEFAULT_VALUE) + "] specified to @Mapping annotation for type " + argument));
                } else {
                    defaultValue = null;
                }

                Object from = values.get(Mapper.Mapping.MEMBER_FROM);
                Object condition = values.get(Mapper.Mapping.MEMBER_CONDITION);
                ArgumentConversionContext<?> finalConversionContext = conversionContext;
                if (from instanceof EvaluatedExpression evaluatedExpression) {
                    customMappers.put(to, (expressionEvaluationContext ->
                        (object, builder) -> {
                            ExpressionEvaluationContext evaluationContext = (ExpressionEvaluationContext) expressionEvaluationContext;
                            if (condition instanceof EvaluatedExpression conditionExpression) {
                                if (ObjectUtils.coerceToBoolean(conditionExpression.evaluate(evaluationContext))) {
                                    Object v = evaluatedExpression.evaluate(evaluationContext);
                                    handleValue(i, argument, defaultValue, finalConversionContext, builder, v);
                                } else if (defaultValue != null) {
                                    builder.with(i, argument, defaultValue);
                                }
                            } else {
                                Object v = evaluatedExpression.evaluate(evaluationContext);
                                handleValue(i, argument, defaultValue, finalConversionContext, builder, v);
                            }
                        }
                    ));
                } else if (from != null) {
                    String propertyName = from.toString();
                    if (fromIntrospection != null) {
                        BeanProperty<Object, Object> fromProperty = fromIntrospection.getRequiredProperty(propertyName, Object.class);
                        customMappers.put(to, (expressionEvaluationContext -> (object, builder) -> {
                            Object result = fromProperty.get(object);
                            handleValue(i, argument, defaultValue, finalConversionContext, builder, result);
                        }));
                    } else if (isMap) {
                        customMappers.put(to, (expressionEvaluationContext -> (object, builder) -> {
                            Object result = ((Map<String, Object>) object).get(propertyName);
                            handleValue(i, argument, defaultValue, finalConversionContext, builder, result);
                        }));
                    }
                }

            }
        }
        return customMappers;
    }

    private void handleValue(int index, Argument<Object> argument, Object defaultValue, ArgumentConversionContext<?> conversionContext, BeanIntrospection.Builder<Object> builder, Object value) {
        if (value == null) {
            if (defaultValue != null) {
                builder.with(index, argument, defaultValue);
            }
        } else if (argument.isInstance(value)) {
            builder.with(index, argument, value);
        } else if (conversionContext != null) {
            builder.convert(index, conversionContext, value, conversionService);
        } else {
            throw new IllegalArgumentException("Cannot map invalid value [" + value + "] to type: " + argument);
        }
    }

    private MapInvocation mapDefault(BeanIntrospection<Object> toIntrospection, BeanIntrospection<Object> fromIntrospection, boolean isMap) {
        MapInvocation invocation;
        if (isMap) {
            invocation = callContext -> map(
                (Map<String, Object>) callContext.getParameterValues()[0],
                MapStrategy.DEFAULT,
                toIntrospection
            );
        } else {
            invocation = callContext -> map(
                callContext.getParameterValues()[0],
                MapStrategy.DEFAULT,
                fromIntrospection,
                toIntrospection
            );
        }
        return invocation;
    }

    private  <I, O> O map(I input, MapStrategy mapStrategy, BeanIntrospection<I> inputIntrospection, BeanIntrospection<O> outputIntrospection) {
        boolean isDefault = mapStrategy == MapStrategy.DEFAULT;
        Mapper.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        BeanIntrospection.Builder<O> builder = outputIntrospection.builder();
        @SuppressWarnings("unchecked") @NonNull Argument<Object>[] arguments = (Argument<Object>[]) builder.getArguments();

        if (!isDefault) {
            processCustomMappers(input, mapStrategy, builder, arguments);
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
                    } else if (conflictStrategy == Mapper.ConflictStrategy.CONVERT) {
                        ArgumentConversionContext<Object> conversionContext = ConversionContext.of(argument);
                        builder.convert(i, conversionContext, value, conversionService);
                    } else {
                        builder.with(i, argument, value);
                    }
                }
            }
        }
        return builder.build();
    }

    private <I, O> void processCustomMappers(I input, MapStrategy mapStrategy, BeanIntrospection.Builder<O> builder, @NonNull Argument<Object>[] arguments) {
        Map<String, BiConsumer<Object, BeanIntrospection.Builder<Object>>> customMappers = mapStrategy.customMappers();
        customMappers.forEach((name, func) -> {
            int i = builder.indexOf(name);
            if (i > -1) {
                func.accept(input, (BeanIntrospection.Builder<Object>) builder);
            }
        });
    }

    private <O> O map(Map<String, Object> input, MapStrategy mapStrategy, BeanIntrospection<O> outputIntrospection) {
        BeanIntrospection.Builder<O> builder = outputIntrospection.builder();
        @NonNull Argument<Object>[] arguments = (Argument<Object>[]) builder.getArguments();
        handleMapInput(input, mapStrategy, builder, arguments);
        return builder.build();
    }

    private <O> void handleMapInput(Map<String, Object> input, MapStrategy mapStrategy, BeanIntrospection.Builder<O> builder, @NonNull Argument<Object>[] arguments) {
        Mapper.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        boolean isDefault = mapStrategy == MapStrategy.DEFAULT;
        if (!isDefault) {
            processCustomMappers(input, mapStrategy, builder, arguments);
        }
        input.forEach((key, value) -> {
            int i = builder.indexOf(key);
            if (!isDefault && mapStrategy.customMappers().containsKey(key)) {
                return;
            }
            if (i > -1) {
                Argument<Object> argument = arguments[i];
                if (conflictStrategy == Mapper.ConflictStrategy.CONVERT) {
                    builder.convert(i, ConversionContext.of(argument), value, conversionService);
                } else {
                    builder.with(i, argument, value);
                }
            }
        });
    }


    @FunctionalInterface
    private interface MapInvocation {
        Object map(MethodInvocationContext<Object, Object> invocationContext);
    }

    private record MapStrategy(Mapper.ConflictStrategy conflictStrategy, Map<String, BiConsumer<Object, BeanIntrospection.Builder<Object>>> customMappers) {
        static final MapStrategy DEFAULT = new MapStrategy(Mapper.ConflictStrategy.CONVERT, Collections.emptyMap());

        private MapStrategy {
            if (conflictStrategy == null) {
                conflictStrategy = Mapper.ConflictStrategy.CONVERT;
            }
            if (customMappers == null) {
                customMappers = new HashMap<>();
            }
        }

        public MapStrategy(Mapper.ConflictStrategy conflictStrategy) {
            this(conflictStrategy, new HashMap<>());
        }
    }
}
