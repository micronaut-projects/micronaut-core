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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Mapper;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * BeanMapper implementation that uses introspections.
 *
 * @since 4.1.0
 */
@Singleton
@Internal
@BootstrapContextCompatible
final class DefaultIntrospectionBeanMapHandler implements IntrospectionBeanMapHandler {
    private final BeanIntrospector beanIntrospector;
    private final ConversionService conversionService;

    /**
     * Constructor that specifies a specific introspector.
     *
     * @param beanIntrospector  The introspector
     * @param conversionService The conversion service
     */
    DefaultIntrospectionBeanMapHandler(@NonNull BeanIntrospector beanIntrospector, ConversionService conversionService) {
        this.beanIntrospector = Objects.requireNonNull(beanIntrospector, "BeanIntrospector cannot be null!");
        this.conversionService = conversionService;
    }

    /**
     * Default constructor.
     * @param conversionService  The conversion service.
     */
    @Inject
    DefaultIntrospectionBeanMapHandler(ConversionService conversionService) {
        this(BeanIntrospector.SHARED, conversionService);
    }

    @Override
    public <I, O> O map(I input, O output, Mapper.MapStrategy mapStrategy, BeanIntrospection<I> left, BeanIntrospection<O> right) {
        if (mapStrategy == null) {
            mapStrategy = Mapper.MapStrategy.DEFAULT;
        }
        Collection<BeanProperty<I, Object>> beanProperties = left.getBeanProperties();
        BeanIntrospection.Builder<O> builder = output != null ? right.builder().with(output) : right.builder();
        @SuppressWarnings("unchecked") @NonNull Argument<Object>[] builderArguments = (Argument<Object>[]) builder.getArguments();
        Mapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        for (BeanProperty<I, Object> beanProperty : beanProperties) {
            if (!beanProperty.isWriteOnly()) {
                int i = builder.indexOf(beanProperty.getName());
                if (i > -1) {
                    Argument<Object> builderArgument = builderArguments[i];
                    if (conflictStrategy == Mapper.MapStrategy.ConflictStrategy.CONVERT) {
                        builder.convert(i, ConversionContext.of(builderArgument), beanProperty.get(input), conversionService);
                    } else {
                        builder.with(i, builderArgument, beanProperty.get(input));
                    }
                }
            }
        }

        return builder.build();
    }

    @Override
    public <I, O> O map(I input, Class<O> outputType, Mapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(outputType, "Output type cannot be null");
        BeanIntrospection<O> right = beanIntrospector.getIntrospection(outputType);
        return map(input, mapStrategy, right);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I, O> O map(I input, Mapper.MapStrategy mapStrategy, BeanIntrospection<O> outputIntrospection) {
        BeanIntrospection<I> left = (BeanIntrospection<I>) beanIntrospector.getIntrospection(input.getClass());
        return map(input, mapStrategy, left, outputIntrospection);
    }

    @Override
    public <I, O> O map(I input, Mapper.MapStrategy mapStrategy, BeanIntrospection<I> inputIntrospection, BeanIntrospection<O> outputIntrospection) {
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

    @Override
    public <O> @NonNull O map(Map<String, Object> input, Class<O> outputType, Mapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(outputType, "Output type cannot be null");
        BeanIntrospection<O> right = beanIntrospector.getIntrospection(outputType);
        return map(input, mapStrategy, right);
    }

    @Override
    public <O> O map(Map<String, Object> input, Mapper.MapStrategy mapStrategy, BeanIntrospection<O> outputIntrospection) {
        Objects.requireNonNull(input, "Input cannot be null");
        if (mapStrategy == null) {
            mapStrategy = Mapper.MapStrategy.DEFAULT;
        }
        BeanIntrospection.Builder<O> builder = outputIntrospection.builder();
        @NonNull Argument<Object>[] arguments = (Argument<Object>[]) builder.getArguments();

        handleMapInput(input, mapStrategy, builder, arguments);
        return builder.build();
    }

    @Override
    public <O> O map(Map<String, Object> input, O output, Mapper.MapStrategy mapStrategy, BeanIntrospection<O> right) {
        if (mapStrategy == null) {
            mapStrategy = Mapper.MapStrategy.DEFAULT;
        }
        if (CollectionUtils.isNotEmpty(input)) {
            BeanIntrospection.Builder<O> builder = right.builder().with(output);
            @NonNull Argument<Object>[] arguments = (Argument<Object>[]) builder.getArguments();
            handleMapInput(
                input,
                mapStrategy,
                builder,
                arguments
            );
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I, O> O map(I input, O output, Mapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(output, "Output cannot be null");
        BeanIntrospection<O> right = (BeanIntrospection<O>) beanIntrospector.getIntrospection(output.getClass());
        if (input != null) {
            BeanIntrospection<I> left = (BeanIntrospection<I>) beanIntrospector.getIntrospection(input.getClass());
            map(input, output, mapStrategy, left, right);
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <O> @NonNull O map(Map<String, Object> input, O output, Mapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(output, "Output cannot be null");
        map(
                input,
                output, mapStrategy,
                (BeanIntrospection<O>) BeanIntrospection.getIntrospection(output.getClass())
        );
        return output;
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
