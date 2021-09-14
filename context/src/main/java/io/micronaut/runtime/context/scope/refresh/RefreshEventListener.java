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
package io.micronaut.runtime.context.scope.refresh;

import java.util.Map;
import java.util.Set;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;

/**
 * A convenience interface that can be implemented if a component needs to listen for {@link io.micronaut.runtime.context.scope.refresh.RefreshEvent} where the implementation is only interested in a limited set of configuration prefixes.
 *
 * @author graemerocher
 * @since 3.1.0
 */
public interface RefreshEventListener extends ApplicationEventListener<RefreshEvent>, Ordered {

    /**
     * The default position as defined by {@link io.micronaut.core.order.Ordered#getOrder()}.
     */
    int DEFAULT_POSITION = Ordered.HIGHEST_PRECEDENCE + 200;

    @Override
    default boolean supports(RefreshEvent event) {
        if (event != null) {
            final Map<String, Object> source = event.getSource();
            if (source != null) {
                if (source == RefreshEvent.ALL_KEYS) {
                    return true;
                }
                final Set<String> keys = source.keySet();
                Set<String> prefixes = getObservedConfigurationPrefixes();
                if (CollectionUtils.isNotEmpty(prefixes)) {
                    for (String prefix : prefixes) {
                        if (keys.stream().anyMatch(k -> k.startsWith(prefix))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the set of observed configuration prefixes that the event listener should listen for.
     * @return A set of prefixes
     */
    @NonNull
    Set<String> getObservedConfigurationPrefixes();

    @Override
    default int getOrder() {
        // run after configuration properties refresh
        return DEFAULT_POSITION;
    }
}
