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
package io.micronaut.management.endpoint.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.UriRoute;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.stream.Stream;

/**
 * <p>Exposes the available management endpoints of the application.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@Controller
@Requires(beans = EmbeddedServer.class)
public final class ManagementController {

    private final HttpHostResolver httpHostResolver;

    private final ManagementRoutesResolver managementRoutesResolver;
    private final ManagementDataCollector<?> managementDataCollector;

    public ManagementController(HttpHostResolver httpHostResolver,
                                ManagementRoutesResolver managementRoutesResolver,
                                ManagementDataCollector<?> managementDataCollector) {
        this.httpHostResolver = httpHostResolver;
        this.managementRoutesResolver = managementRoutesResolver;
        this.managementDataCollector = managementDataCollector;
    }

    @Get("management")
    public Mono<?> getManagementRoutes(HttpRequest<?> httpRequest) {
        String routeBase = httpHostResolver.resolve(httpRequest);
        String managementDiscoveryPath = httpRequest.getPath();
        Stream<UriRoute> uriRoutes = managementRoutesResolver.getRoutes();

        return Mono.just(managementDataCollector.collectData(uriRoutes, routeBase,
                managementDiscoveryPath, false));
    }
}
