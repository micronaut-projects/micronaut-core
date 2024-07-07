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
package io.micronaut.scheduling;

import io.micronaut.core.exceptions.BeanExceptionHandler;
import io.micronaut.inject.BeanDefinition;

/**
 * An exception handler interface for task related exceptions.
 *
 * @param <T> The generic type of the task bean
 * @param <E> The generic type of the exception
 * @author graemerocher
 * @since 1.0
 */
public interface TaskExceptionHandler<T, E extends Throwable> extends BeanExceptionHandler<T, E> {

    /**
     * Handle an error that occurs during creation of the scheduled task.
     *
     * @param beanType The bean type
     * @param throwable The throwable
     * @since 4.0.0
     */
    default void handleCreationFailure(BeanDefinition<T> beanType, E throwable) {
        if (DefaultTaskExceptionHandler.LOG.isErrorEnabled()) {
            var message = new StringBuilder("Error creating scheduled task ");
            if (beanType != null) {
                message.append("for bean [").append(beanType.asArgument()).append("] ");
            }
            message.append(throwable.getMessage());
            DefaultTaskExceptionHandler.LOG.error(message.toString(), throwable);
        }
    }
}
