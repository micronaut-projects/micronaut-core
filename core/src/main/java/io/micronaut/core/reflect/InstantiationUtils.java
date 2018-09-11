/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.reflect.exception.InstantiationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.function.Function;

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
                .map((Function<Class, Optional>) InstantiationUtils::tryInstantiate);
        } catch (Throwable e) {
            Logger log = LoggerFactory.getLogger(InstantiationUtils.class);
            if (log.isDebugEnabled()) {
                log.debug("Tried, but could not instantiate type: " + name, e);
            }
            return Optional.empty();
        }
    }

    /**
     * Try to instantiate the given class.
     *
     * @param type The type
     * @param <T>  The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static <T> Optional<T> tryInstantiate(Class<T> type) {
        try {
            T bean = type.newInstance();
            if (type.isInstance(bean)) {
                return Optional.of(bean);
            }
            return Optional.empty();
        } catch (Throwable e) {
            try {
                Constructor<T> defaultConstructor = type.getDeclaredConstructor();
                defaultConstructor.setAccessible(true);
                return tryInstantiate(defaultConstructor);
            } catch (Throwable e1) {
                Logger log = LoggerFactory.getLogger(InstantiationUtils.class);
                if (log.isDebugEnabled()) {
                    log.debug("Tried, but could not instantiate type: " + type, e);
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Try to instantiate the given class.
     *
     * @param type The type
     * @param args The arguments to the constructor
     * @param <T>  The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static <T> Optional<T> tryInstantiate(Constructor<T> type, Object... args) {
        try {
            return Optional.of(type.newInstance(args));
        } catch (Throwable e) {
            Logger log = LoggerFactory.getLogger(InstantiationUtils.class);
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
            return type.newInstance();
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
