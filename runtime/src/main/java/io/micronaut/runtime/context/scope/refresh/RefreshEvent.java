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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.event.ApplicationEvent;

import java.util.Collections;
import java.util.Map;

/**
 * <p>An {@link ApplicationEvent} for handling refreshes.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class RefreshEvent extends ApplicationEvent {

    static final Map<String, Object> ALL_KEYS = Collections.singletonMap("all", "*");

    /**
     * Constructs a prototypical Event.
     *
     * @param changes The keys that changed and the previous values of said keys
     * @throws IllegalArgumentException if source is null.
     */
    public RefreshEvent(Map<String, Object> changes) {
        super(changes);
    }

    /**
     * Constructs a refresh Event that refreshes all keys.
     */
    public RefreshEvent() {
        super(ALL_KEYS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getSource() {
        return (Map<String, Object>) super.getSource();
    }
}
