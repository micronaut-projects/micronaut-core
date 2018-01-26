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
package org.particleframework.discovery.consul

import org.particleframework.core.async.publisher.Publishers
import org.particleframework.discovery.consul.client.v1.CatalogEntry
import org.particleframework.discovery.consul.client.v1.ConsulOperations
import org.particleframework.discovery.consul.client.v1.HealthEntry
import org.particleframework.discovery.consul.client.v1.NewServiceEntry
import org.particleframework.discovery.consul.client.v1.ServiceEntry
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.*
import org.particleframework.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher

import javax.inject.Singleton
import javax.validation.constraints.NotNull
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple server that mocks the Consul API
 *
 * @author graemerocher
 * @since 1.0
 */
@Controller("/v1")
@Singleton
class MockConsulServer implements ConsulOperations {

    Map<String, ServiceEntry> services = new ConcurrentHashMap<>()
    final CatalogEntry nodeEntry

    MockConsulServer(EmbeddedServer embeddedServer) {
        nodeEntry = new CatalogEntry(UUID.randomUUID().toString(), InetAddress.localHost)
    }

    @Override
    Publisher<Boolean> register(@NotNull @Body CatalogEntry entry) {
        return Publishers.just(true)
    }

    @Override
    Publisher<Boolean> deregister(@NotNull @Body CatalogEntry entry) {
        return Publishers.just(true)
    }

    @Override
    Publisher<HttpStatus> register(@NotNull @Body NewServiceEntry entry) {
        services.put(entry.getName(), new ServiceEntry(entry))
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> deregister(@NotNull String service) {
        services.remove(service)
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<Map<String, ServiceEntry>> getServices() {
        return Publishers.just(services)
    }

    @Override
    Publisher<List<HealthEntry>> getHealthyServices(@NotNull String service) {
        return Publishers.just(services.values().collect {

            def entry = new HealthEntry()
            entry.setNode(nodeEntry)
            entry.setService(it)
            return entry
        } as List<HealthEntry>)
    }

    @Override
    Publisher<List<CatalogEntry>> getNodes() {
        return Publishers.just([nodeEntry])
    }

    @Override
    Publisher<List<CatalogEntry>> getNodes(@NotNull String datacenter) {
        return Publishers.just([nodeEntry])
    }

    @Override
    Publisher<Map<String, List<String>>> getServiceNames() {
        return Publishers.just(services.collectEntries { String key, ServiceEntry entry ->
              return [(key): entry.tags]
        })
    }
}
