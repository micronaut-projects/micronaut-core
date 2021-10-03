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

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.management.endpoint.management.impl.DefaultManagementDataCollector;
import io.micronaut.web.router.UriRoute;
import org.reactivestreams.Publisher;

import java.util.stream.Stream;

/**
 * <p>Collect management data to respond in {@link ManagementController}.</p>
 *
 * @param <T> type returned by the {@link Publisher}.
 *
 * @author Hernán Cervera
 * @since 3.0.0
 * */
@DefaultImplementation(DefaultManagementDataCollector.class)
public interface ManagementDataCollector<T> {

    /**
     * Collect management data.
     *
     * @param routes management routes.
     * @param routeBase base route of the endpoints.
     * @param managementDiscoveryPath path of the management endpoint exposed by {@link ManagementController}.
     * @param isManagementDiscoveryPathTemplated whether the discovery endpoint path is templated.
     * @return the {@link Publisher}.
     */
    T collectData(Stream<UriRoute> routes, String routeBase,
                             String managementDiscoveryPath, boolean isManagementDiscoveryPathTemplated);
}
