/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.reflect;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility methods for instantiating objects.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class InstantiationUtils {


    /**
     * Try to instantiate the given class.
     *
     * @param name        The class name
     * @param classLoader The class loader to use
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static Optional<?> tryInstantiate(String name, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(name, classLoader)
                .flatMap(InstantiationUtils::tryInstantiate);
        } catch (Throwable e) {
            Logger log = LoggerFactory.getLogger(InstantiationUtils.class);
            if (log.isDebugEnabled()) {
                log.debug("Tried, but could not instantiate type: " + name, e);
            }
            return Optional.empty();
        }
    }

    /**
     * Try to instantiate the given class using {@link io.micronaut.core.beans.BeanIntrospector}.
     *
     * @param type The type
     * @param propertiesMap The properties values {@link Map} of the instance
     * @param context The Conversion context
     * @param <T> The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     * @throws InstantiationException When an error occurs
     */
    public static @Nonnull <T> Optional<T> tryInstantiate(@Nonnull Class<T> type, Map propertiesMap, ConversionContext context) {
        ArgumentUtils.requireNonNull("type", type);
        if (propertiesMap.isEmpty()) {
            return tryInstantiate(type);
        }

        final Supplier<T> reflectionFallback = () -> {
            Logger log = LoggerFactory.getLogger(InstantiationUtils.class);
            if (log.isDebugEnabled()) {
                log.debug("Tried, but could not instantiate type: " + type);
            }
            return null;
        };

        T result = BeanIntrospector.SHARED.findIntrospection(type).map(introspection -> {
            T instance;
            Argument[] constructorArguments = introspection.getConstructorArguments();
            List<Object> arguments = new ArrayList<>(constructorArguments.length);

            try {
                if (constructorArguments.length > 0) {

                    Map bindMap = new LinkedHashMap(propertiesMap.size());
                    Set<Map.Entry<?, ?>> entries = propertiesMap.entrySet();
                    for (Map.Entry<?, ?> entry : entries) {
                        Object key = entry.getKey();
                        bindMap.put(NameUtils.decapitalize(NameUtils.dehyphenate(key.toString())), entry.getValue());
                    }

                    for (Argument<?> argument : constructorArguments) {
                        if (bindMap.containsKey(argument.getName())) {
                            Object converted = ConversionService.SHARED.convert(bindMap.get(argument.getName()), argument.getType(), ConversionContext.of(argument)).orElseThrow(() ->
                                    new ConversionErrorException(argument, context.getLastError()
                                            .orElse(() -> new IllegalArgumentException("Value [" + bindMap.get(argument.getName()) + "] cannot be converted to type : " + argument.getType())))
                            );
                            arguments.add(converted);
                        } else if (argument.isDeclaredNullable()) {
                            arguments.add(null);
                        } else {
                            context.reject(new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName())));
                        }
                    }

                    instance = introspection.instantiate(arguments.toArray());
                } else {
                    instance = introspection.instantiate();
                }
                return instance;
            } catch (InstantiationException e) {
                return reflectionFallback.get();
            }
        }).orElseGet(reflectionFallback);
        return Optional.ofNullable(result);
    }

    /**
     * Try to instantiate the given class using the most optimal strategy first trying the {@link io.micronaut.core.beans.BeanIntrospector} and
     * if no bean is present falling back to reflection.
     *
     * @param type The type
     * @param <T>  The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static @Nonnull <T> Optional<T> tryInstantiate(@Nonnull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        final Supplier<T> reflectionFallback = () -> {
            final Logger logger = ClassUtils.REFLECTION_LOGGER;
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot instantiate type [{}] without reflection. Attempting reflective instantiation", type);
            }
            try {
                T bean = type.newInstance();
                if (type.isInstance(bean)) {
                    return bean;
                }
                return null;
            } catch (Throwable e) {
                try {
                    Constructor<T> defaultConstructor = type.getDeclaredConstructor();
                    defaultConstructor.setAccessible(true);
                    return tryInstantiate(defaultConstructor).orElse(null);
                } catch (Throwable e1) {
                    Logger log = LoggerFactory.getLogger(InstantiationUtils.class);
                    if (log.isDebugEnabled()) {
                        log.debug("Tried, but could not instantiate type: " + type, e);
                    }
                    return null;
                }
            }
        };
        final T result = BeanIntrospector.SHARED.findIntrospection(type).map(introspection -> {
            try {
                return introspection.instantiate();
            } catch (InstantiationException e) {
                return reflectionFallback.get();
            }
        }).orElseGet(reflectionFallback);

        return Optional.ofNullable(result);
    }

    /**
     * Try to instantiate the given class.
     *
     * @param type The type
     * @param args The arguments to the constructor
     * @param <T>  The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static @Nonnull <T> Optional<T> tryInstantiate(@Nonnull Constructor<T> type, Object... args) {
        try {
            return Optional.of(type.newInstance(args));
        } catch (Throwable e) {
            Logger log = ClassUtils.REFLECTION_LOGGER;
            if (log.isDebugEnabled()) {
                log.debug("Tried, but could not instantiate type: " + type, e);
            }
            return Optional.empty();
        }
    }

    /**
     * Instantiate the given class rethrowing any exceptions as {@link InstantiationException}.
     *
     * @param type The type
     * @param <T>  The generic type
     * @return The instantiated instance
     * @throws InstantiationException When an error occurs
     */
    public static <T> T instantiate(Class<T> type) {
        try {
            return BeanIntrospector.SHARED.findIntrospection(type).map(BeanIntrospection::instantiate).orElseGet(() -> {
                try {
                    Logger log = ClassUtils.REFLECTION_LOGGER;
                    if (log.isDebugEnabled()) {
                        log.debug("Reflectively instantiating type: " + type);
                    }
                    return type.newInstance();
                } catch (Throwable e) {
                    throw new InstantiationException("Could not instantiate type [" + type.getName() + "]: " + e.getMessage(), e);
                }
            });
        } catch (Throwable e) {
            throw new InstantiationException("Could not instantiate type [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Instantiate the given class rethrowing any exceptions as {@link InstantiationException}.
     *
     * @param type        The type
     * @param classLoader The classloader
     * @return The instantiated instance
     * @throws InstantiationException When an error occurs
     */
    public static Object instantiate(String type, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(type, classLoader)
                .flatMap(InstantiationUtils::tryInstantiate)
                .orElseThrow(() -> new InstantiationException("No class found for name: " + type));
        } catch (Throwable e) {
            throw new InstantiationException("Could not instantiate type [" + type + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Instantiate the given class rethrowing any exceptions as {@link InstantiationException}.
     *
     * @param type         The type
     * @param requiredType The required type
     * @param <T>          The type
     * @return The instantiated instance
     * @throws InstantiationException When an error occurs
     */
    public static <T> T instantiate(String type, Class<T> requiredType) {
        try {
            return ClassUtils.forName(type, requiredType.getClassLoader())
                .flatMap((Function<Class, Optional<T>>) aClass -> {
                    if (requiredType == aClass || requiredType.isAssignableFrom(aClass)) {
                        return tryInstantiate(aClass);
                    }
                    return Optional.empty();
                })
                .orElseThrow(() -> new InstantiationException("No compatible class found for name: " + type));
        } catch (Throwable e) {
            throw new InstantiationException("Could not instantiate type [" + type + "]: " + e.getMessage(), e);
        }
    }
}
