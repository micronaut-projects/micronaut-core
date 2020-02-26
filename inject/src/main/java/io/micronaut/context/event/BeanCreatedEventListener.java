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
package io.micronaut.context.event;

import io.micronaut.core.annotation.Indexed;

import java.util.EventListener;

/**
 * <p>An event listener that is triggered each time a bean is created.</p>
 * <p>
 * <p>Allows customization of the created beans.</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @see BeanCreatedEvent
 * @since 1.0
 */
@Indexed(BeanCreatedEventListener.class)
public interface BeanCreatedEventListener<T> extends EventListener {

    /**
     * Fired when a bean is created and all {@link javax.annotation.PostConstruct} initialization hooks have been
     * called.
     *
     * @param event The bean created event
     * @return The bean or a replacement bean of the same type
     */
    T onCreated(BeanCreatedEvent<T> event);
}
