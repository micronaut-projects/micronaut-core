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
package io.micronaut.websocket.context;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.MethodExecutionHandle;

import java.util.Optional;

/**
 * Wrapper around a WebSocket instance that enables the retrieval of the appropriate methods.
 *
 * @param <T> The target type
 * @author graemerocher
 * @since 1.0
 */
public interface WebSocketBean<T> {

    /**
     * The bean definition.
     * @return The bean definition
     */
    BeanDefinition<T> getBeanDefinition();

    /**
     * @return The target instance
     */
    T getTarget();

    /**
     * Returns the method annotated with {@link io.micronaut.websocket.annotation.OnMessage}.
     *
     * @return the method
     */
    Optional<MethodExecutionHandle<T, ?>> messageMethod();

    /**
     * Returns the method annotated with {@link io.micronaut.websocket.annotation.OnClose}.
     *
     * @return the method
     */
    Optional<MethodExecutionHandle<T, ?>> closeMethod();

    /**
     * Returns the method annotated with {@link io.micronaut.websocket.annotation.OnOpen}.
     *
     * @return the method
     */
    Optional<MethodExecutionHandle<T, ?>> openMethod();

    /**
     * Returns the method annotated with {@link io.micronaut.websocket.annotation.OnError}.
     *
     * @return the method
     */
    Optional<MethodExecutionHandle<T, ?>> errorMethod();

}
