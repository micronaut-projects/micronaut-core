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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMapper;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.UnsafeBeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
final class DefaultIntrospectionBeanMapHandler implements IntrospectionBeanMapHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultIntrospectionBeanMapHandler.class);
    private static final String PREFIX_UNABLE_BIND_PROPERTY = "Unable bind property [";
    private static final String MSG_TO_TARGET_BEAN = "] to target bean [";
    private static final String PREFIX_NON_NULL_ARG = "Non-null constructor argument specified as null: ";
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
    public <I, O> O map(I input, O output, BeanMapper.MapStrategy mapStrategy, BeanIntrospection<I> left, BeanIntrospection<O> right) {
        if (mapStrategy == null) {
            mapStrategy = BeanMapper.MapStrategy.DEFAULT;
        }
        Collection<BeanProperty<I, Object>> beanProperties = left.getBeanProperties();
        BeanMapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        if (conflictStrategy == null || conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.ERROR) {

                for (BeanProperty<I, Object> leftProperty : beanProperties) {
                    try {
                        BeanProperty<O, Object> rightProperty =
                                right.getRequiredProperty(leftProperty.getName(), leftProperty.getType());
                        if (!rightProperty.isReadOnly()) {
                            ((UnsafeBeanProperty<O, Object>) rightProperty).setUnsafe(output, leftProperty.get(input));
                        }
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failure handling property [" + leftProperty + "] from input [" + input + "]: " + e.getMessage(), e);
                    }
                }

        } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.CONVERT) {

            for (BeanProperty<I, Object> leftProperty : beanProperties) {
                try {
                    BeanProperty<O, Object> rightProperty =
                            right.getProperty(leftProperty.getName()).orElse(null);
                    if (rightProperty != null && !rightProperty.isReadOnly()) {
                        rightProperty.convertAndSet(output, leftProperty.get(input));
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failure handling property [" + leftProperty + "] from input [" + input + "]: " + e.getMessage(), e);
                }
            }

        } else {
            for (BeanProperty<I, Object> leftProperty : beanProperties) {
                BeanProperty<O, Object> rightProperty =
                        right.getProperty(leftProperty.getName(), leftProperty.getType()).orElse(null);
                if (rightProperty != null && !rightProperty.isReadOnly()) {
                    rightProperty.set(output, leftProperty.get(input));
                } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.WARN) {
                    LOG.warn("Unable bind property [{}] to target bean [{}]. No matching property found", leftProperty, output.getClass());
                }
            }
        }
        return output;
    }

    @Override
    public <I, O> O map(I input, Class<O> outputType, BeanMapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(outputType, "Output type cannot be null");
        BeanIntrospection<O> right = beanIntrospector.getIntrospection(outputType);
        return map(input, mapStrategy, right);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I, O> O map(I input, BeanMapper.MapStrategy mapStrategy, BeanIntrospection<O> outputIntrospection) {
        BeanIntrospection<I> left = (BeanIntrospection<I>) beanIntrospector.getIntrospection(input.getClass());
        return map(input, mapStrategy, left, outputIntrospection);
    }

    @Override
    public <I, O> O map(I input, BeanMapper.MapStrategy mapStrategy, BeanIntrospection<I> inputIntrospection, BeanIntrospection<O> outputIntrospection) {
        if (mapStrategy == null) {
            mapStrategy = BeanMapper.MapStrategy.DEFAULT;
        }
        @NonNull Argument<?>[] constructorArguments = outputIntrospection.getConstructorArguments();
        BeanMapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        if (constructorArguments.length == 0) {
            O output = outputIntrospection.instantiate();
            if (input != null) {
                return map(input, output, mapStrategy, inputIntrospection, outputIntrospection);
            }
            return output;
        } else {
            Objects.requireNonNull(input, "Input cannot be null");
            // constructor binding
            Object[] parameters = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; i++) {
                Argument<?> constructorArgument = constructorArguments[i];
                BeanProperty<I, ?> property = inputIntrospection.getProperty(constructorArgument.getName(), constructorArgument.getType()).orElse(null);
                if (property != null) {
                    Object v = property.get(input);
                    if (v != null) {
                        parameters[i] = v;
                    } else if (constructorArgument.isDeclaredNonNull()) {
                        throw new IllegalArgumentException(PREFIX_NON_NULL_ARG + constructorArgument.getName());
                    }
                } else if (constructorArgument.isDeclaredNonNull()) {
                    throw new IllegalArgumentException(PREFIX_NON_NULL_ARG + constructorArgument.getName());
                }
            }
            O output = outputIntrospection.instantiate(parameters);
            return map(input, output, mapStrategy, inputIntrospection, outputIntrospection);
        }
    }

    @Override
    public <O> @NonNull O map(Map<String, Object> input, Class<O> outputType, BeanMapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(outputType, "Output type cannot be null");
        BeanIntrospection<O> right = beanIntrospector.getIntrospection(outputType);
        return map(input, mapStrategy, right);
    }

    @Override
    public <O> O map(Map<String, Object> input, BeanMapper.MapStrategy mapStrategy, BeanIntrospection<O> outputIntrospection) {
        if (mapStrategy == null) {
            mapStrategy = BeanMapper.MapStrategy.DEFAULT;
        }
        BeanMapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        @NonNull Argument<?>[] constructorArguments = outputIntrospection.getConstructorArguments();
        if (constructorArguments.length == 0) {
            O output = outputIntrospection.instantiate();
            return map(input, output, mapStrategy, outputIntrospection);
        } else {
            Objects.requireNonNull(input, "Input cannot be null");
            // constructor binding
            Object[] parameters = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; i++) {
                Argument<?> constructorArgument = constructorArguments[i];
                String key = constructorArgument.getName();
                if (input.containsKey(key)) {
                    Object v = input.get(key);
                    if (v != null) {
                        if (constructorArgument.isInstance(v)) {
                            parameters[i] = v;
                        } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.CONVERT) {
                            parameters[i] = conversionService.convertRequired(v, constructorArgument);
                        }
                    } else if (constructorArgument.isDeclaredNonNull()) {
                        throw new IllegalArgumentException(PREFIX_NON_NULL_ARG + constructorArgument.getName());
                    }
                } else {
                    throw new IllegalArgumentException("Missing constructor argument from input: " + key);
                }
            }
            O output = outputIntrospection.instantiate(parameters);
            return map(input, output, mapStrategy, outputIntrospection);
        }
    }

    @Override
    public <O> O map(Map<String, Object> input, O output, BeanMapper.MapStrategy mapStrategy, BeanIntrospection<O> right) {
        if (mapStrategy == null) {
            mapStrategy = BeanMapper.MapStrategy.DEFAULT;
        }
        BeanMapper.MapStrategy.ConflictStrategy conflictStrategy = mapStrategy.conflictStrategy();
        if (CollectionUtils.isNotEmpty(input)) {
            boolean shouldError = conflictStrategy == null || conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.ERROR;
            input.forEach((key, value) -> {
                boolean isNonNull = value != null;
                if (shouldError) {
                    mapOrError(output, right, key, value, isNonNull);
                } else {
                    mapEntry(output, conflictStrategy, right, key, value, isNonNull);
                }
            });
        }
        return output;
    }

    private static <O> void mapEntry(O output, BeanMapper.MapStrategy.ConflictStrategy conflictStrategy, BeanIntrospection<O> right, String key, Object value, boolean isNonNull) {
        BeanProperty<O, Object> property = right.getProperty(key).orElse(null);
        if (property != null) {
            if (!property.isReadOnly()) {
                if (isNonNull) {
                    if (property.getClass().isInstance(value)) {
                        ((UnsafeBeanProperty<O, Object>) property).setUnsafe(output, value);
                    } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.CONVERT) {
                        property.convertAndSet(output, value);
                    }
                } else if ((property.isDeclaredNullable() || !property.isDeclaredNonNull()) && !property.getType().isPrimitive()) {
                    ((UnsafeBeanProperty<O, Object>) property).setUnsafe(output, null);
                } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.CONVERT) {
                    throw new IllegalArgumentException(PREFIX_UNABLE_BIND_PROPERTY + key + MSG_TO_TARGET_BEAN + output.getClass() + "]. Property cannot be set to null.");
                } else {
                    LOG.warn("Unable bind property [{}}] to target bean [{}}]. Property cannot be set to null.", key, output.getClass());
                }
            }
        } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.WARN) {
            LOG.warn("Unable bind property [{}}] to target bean [{}}]. No matching property found", key, output.getClass());
        } else if (conflictStrategy == BeanMapper.MapStrategy.ConflictStrategy.CONVERT) {
            throw new IllegalArgumentException(PREFIX_UNABLE_BIND_PROPERTY + key + MSG_TO_TARGET_BEAN + output.getClass() + "]. No matching property found");
        }
    }

    private static <O> void mapOrError(O output, BeanIntrospection<O> right, String key, Object value, boolean isNonNull) {
        try {
            if (isNonNull) {
                @SuppressWarnings("unchecked")
                BeanProperty<O, Object> property = (BeanProperty<O, Object>) right.getRequiredProperty(key, ReflectionUtils.getPrimitiveType(value.getClass()));
                if (!property.isReadOnly()) {
                    ((UnsafeBeanProperty<O, Object>) property).setUnsafe(output, value);
                }
            } else {
                BeanProperty<O, Object> property = right.getRequiredProperty(key, Object.class);
                if ((property.isDeclaredNullable() || !property.isDeclaredNonNull()) && !property.getType().isPrimitive()) {
                    ((UnsafeBeanProperty<O, Object>) property).setUnsafe(output, null);
                } else {
                    throw new IllegalArgumentException("Cannot set non-null property [" + property + "] to null");
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failure handling key [" + key + "] of type [" + (value != null ? value.getClass() : null) + "]: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I, O> O map(I input, O output, BeanMapper.MapStrategy mapStrategy) {
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
    public <O> @NonNull O map(Map<String, Object> input, O output, BeanMapper.MapStrategy mapStrategy) {
        Objects.requireNonNull(output, "Output cannot be null");
        map(
                input,
                output, mapStrategy,
                (BeanIntrospection<O>) BeanIntrospection.getIntrospection(output.getClass())
        );
        return output;
    }
}
