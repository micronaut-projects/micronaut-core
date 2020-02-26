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
package io.micronaut.cli.profile.commands.events

import groovy.transform.CompileStatic

/**
 * Stores command line events
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class EventStorage {

    private static Map<String, Collection<Closure>> eventListeners = [:].withDefault { [] }

    static void registerEvent(String eventName, Closure callable) {
        if (!eventListeners[eventName].contains(callable)) {
            eventListeners[eventName] << callable
        }
    }

    static void fireEvent(Object caller, String eventName, Object... args) {
        def listeners = eventListeners[eventName]
        for (listener in listeners) {
            listener.delegate = caller
            listener.call args
        }
    }
}
