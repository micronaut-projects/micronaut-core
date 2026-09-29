/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.annotation.Experimental;

import java.util.Objects;

/**
 * Published once a configuration refresh ran its phases: the configuration beans are rebound, the
 * refreshable beans disposed of and the watches told. A listener reads the new values; it does not
 * have to apply anything itself, a configuration watch is for that.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class ConfigurationRefreshedEvent extends ApplicationEvent {

    private final RefreshResult result;

    /**
     * Creates the event.
     *
     * @param source The refresher
     * @param result The result of the refresh
     */
    public ConfigurationRefreshedEvent(Object source, RefreshResult result) {
        super(source);
        this.result = Objects.requireNonNull(result, "result");
    }

    /**
     * @return The change applied
     */
    public ConfigurationChange change() {
        return result.change();
    }

    /**
     * @return The result
     */
    public RefreshResult result() {
        return result;
    }
}
