/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context.event;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;

import java.util.EventListener;

/**
 * <p>An event listener that is triggered before a bean is destroyed.</p>
 * <p>
 * <p>Allows customization of the bean destruction.</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @see BeanPreDestroyEvent
 * @since 3.0.0
 */
@Indexed(BeanPreDestroyEventListener.class)
@FunctionalInterface
public interface BeanPreDestroyEventListener<T> extends EventListener {
    /**
     * Fired when a bean is is about to be destroyed but before any {@link jakarta.annotation.PreDestroy} methods have been invoked.
     *
     * @param event The bean created event
     * @return The bean or a replacement bean of the same type
     */
    @NonNull T onPreDestroy(@NonNull BeanPreDestroyEvent<T> event);
}
