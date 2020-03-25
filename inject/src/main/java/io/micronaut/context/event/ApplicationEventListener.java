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
 * An interface for receivers of application events.
 *
 * @param <E> An event
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(ApplicationEventListener.class)
public interface ApplicationEventListener<E> extends EventListener {

    /**
     * Handle an application event.
     *
     * @param event the event to respond to
     */
    void onApplicationEvent(E event);

    /**
     * Whether the given event is supported.
     *
     * @param event The event
     * @return True if it is
     */
    default boolean supports(E event) {
        return true;
    }
}
