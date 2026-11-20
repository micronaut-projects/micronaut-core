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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * A source of gateway routes that can change while the application runs, like Spring Cloud
 * Gateway's {@code RouteLocator}, e.g. routes kept in a database or derived from the services of
 * a discovery client. The routes of the locator beans are loaded at startup and again on every
 * refresh: {@code POST /gateway/refresh} of the gateway endpoint, a
 * {@code RefreshEvent}, or every {@code micronaut.gateway.refresh-interval}. A refresh replaces
 * them all at once; a refresh that fails keeps the last routes. They come after the routes of
 * the {@link GatewayRoutes} beans and of the configuration, and never shadow them or another
 * route of the application.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface GatewayRouteLocator {

    /**
     * @return The current routes of this locator
     */
    CompletionStage<List<GatewayRoute>> getRoutes();
}
