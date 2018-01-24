/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.consul;

import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.annotation.Put;
import org.particleframework.http.client.Client;
import org.reactivestreams.Publisher;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * A non-blocking HTTP client for consul
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Client(id = ConsulClient.SERVICE_ID, path = "/v1")
public interface ConsulClient {
    String SERVICE_ID = "consul";
    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html
     * @param entry The entry to register
     *
     * @return
     */
    @Put("/catalog/register")
    Publisher<HttpResponse<?>> register(@NotNull CatalogEntry entry);

    /**
     * Gets all of the nodes
     *
     * @return All the nodes
     */
    @Get("/catalog/nodes")
    Publisher<CatalogEntry> getNodes();

    /**
     * Gets all the nodes for the given data center
     *
     * @param datacenter The data center
     * @return A publisher that emits the nodes
     */
    @Get("/catalog/nodes?dc={datacenter}")
    Publisher<CatalogEntry> getNodes(@NotNull String datacenter);

    /**
     * Gets all of the service names and optional tags
     *
     * @return A Map where the keys are service names and the values are service tags
     */
    @Get("/catalog/services")
    Publisher<Map<String, List<String>>> getServiceNames();
}
