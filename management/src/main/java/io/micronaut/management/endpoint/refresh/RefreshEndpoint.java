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
package io.micronaut.management.endpoint.refresh;

import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventPublisher;
import org.jspecify.annotations.Nullable;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Write;
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import jakarta.inject.Inject;

import java.util.Map;


import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * <p>Exposes an {@link Endpoint} to refresh application state via a {@link RefreshEvent}.</p>
 *
 * @author Graeme Rocher
 * @see io.micronaut.runtime.context.scope.refresh.RefreshScope
 * @see io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
 * @see io.micronaut.runtime.context.scope.Refreshable
 * @since 1.0
 */
@Endpoint("refresh")
public class RefreshEndpoint {

    @Nullable
    private final ConfigurationRefresher refresher;
    @Nullable
    private final Environment environment;
    @Nullable
    private final ApplicationEventPublisher<RefreshEvent> eventPublisher;

    /**
     * @param refresher The refresher, which runs the phases of a refresh
     * @since 5.3.0
     */
    @Inject
    public RefreshEndpoint(ConfigurationRefresher refresher) {
        this.refresher = refresher;
        this.environment = null;
        this.eventPublisher = null;
    }

    /**
     * @param environment    The Environment
     * @param eventPublisher The Application event publisher
     * @deprecated The endpoint runs through the {@link ConfigurationRefresher}; use {@link #RefreshEndpoint(ConfigurationRefresher)}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    public RefreshEndpoint(Environment environment, ApplicationEventPublisher<RefreshEvent> eventPublisher) {
        this.refresher = null;
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

        if (refresher == null) {
            return refreshByEvent(force);
        }
        if (force != null && force) {
            // the property sources are read again first, so that everything is applied from the new values
            refresher.refreshAll();
            return EMPTY_STRING_ARRAY;
        }
        return refresher.refresh().change().changed().toArray(EMPTY_STRING_ARRAY);
    }

    /**
     * The path of the deprecated constructor: the event, which the refresh scope now hands to the refresher.
     */
    private String[] refreshByEvent(@Nullable Boolean force) {
        if (environment == null || eventPublisher == null) {
            return EMPTY_STRING_ARRAY;
        }
        if (force != null && force) {
            eventPublisher.publishEvent(new RefreshEvent());
            return EMPTY_STRING_ARRAY;
        }
        Map<String, Object> changes = environment.refreshAndDiff();
        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new RefreshEvent(changes));
        }
        return changes.keySet().toArray(EMPTY_STRING_ARRAY);
    }
}
