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
package io.micronaut.management.endpoint.refresh;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Write;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * <p>Exposes an {@link Endpoint} to refresh application state via a {@link RefreshEvent}.</p>
 *
 * @author Graeme Rocher
 * @see io.micronaut.runtime.context.scope.refresh.RefreshScope
 * @see io.micronaut.runtime.context.scope.refresh.RefreshEvent
 * @see io.micronaut.runtime.context.scope.Refreshable
 * @since 1.0
 */
@Endpoint("refresh")
public class RefreshEndpoint {

    private final Environment environment;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * @param environment    The Environment
     * @param eventPublisher The Application event publiser
     */
    public RefreshEndpoint(Environment environment, ApplicationEventPublisher eventPublisher) {
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Refresh application state only if environment has changed (unless <code>force</code> is set to true).
     *
     * @param force {@link Nullable} body property to indicate whether to force all {@link io.micronaut.runtime.context.scope.Refreshable} beans to be refreshed
     * @return array of change keys if applicable
     */
    @Write
    public String[] refresh(@Nullable Boolean force) {

        if (force != null && force) {
            eventPublisher.publishEvent(new RefreshEvent());
            return new String[0];
        } else {
            Map<String, Object> changes = environment.refreshAndDiff();
            if (!changes.isEmpty()) {
                eventPublisher.publishEvent(new RefreshEvent(changes));
            }
            Set<String> keys = changes.keySet();
            return keys.toArray(new String[0]);
        }
    }
}
