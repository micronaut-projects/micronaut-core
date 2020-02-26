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
package io.micronaut.core.exceptions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * An exception handler capable of receiving a bean that originated the exception and an exception type.
 *
 * @param <T> The bean type
 * @param <E> And the exception
 */
@FunctionalInterface
public interface BeanExceptionHandler<T, E extends Throwable> extends BiConsumer<T, E> {

    /**
     * Handles the exception.
     *
     * @param bean The bean
     * @param throwable The error
     */
    void handle(@Nullable T bean, @Nonnull E throwable);

    /**
     * Handles the exception.
     *
     * @param bean The bean
     * @param throwable The error
     */
    default void accept(@Nullable T bean, @Nonnull E throwable) {
        handle(bean, throwable);
    }
}
