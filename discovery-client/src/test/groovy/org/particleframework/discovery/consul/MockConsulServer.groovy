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

import org.particleframework.context.annotation.Requires
import org.particleframework.core.async.publisher.Publishers
import org.particleframework.discovery.consul.client.v1.CatalogEntry
import org.particleframework.discovery.consul.client.v1.Check
import org.particleframework.discovery.consul.client.v1.ConsulOperations
import org.particleframework.discovery.consul.client.v1.HealthEntry
import org.particleframework.discovery.consul.client.v1.MockCheckEntry
import org.particleframework.discovery.consul.client.v1.MockHealthEntry
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
@Requires(property = MockConsulServer.ENABLED)
class MockConsulServer implements ConsulOperations {
    public static final String ENABLED = 'enable.mock.consul'

    Map<String, ServiceEntry> services = new ConcurrentHashMap<>()
    Map<String, MockCheckEntry> checks = new ConcurrentHashMap<>()
    final CatalogEntry nodeEntry

    static NewServiceEntry lastNewEntry
    static List<String> passingReports = []

    MockConsulServer(EmbeddedServer embeddedServer) {
        lastNewEntry = null
        passingReports.clear()
        nodeEntry = new CatalogEntry(UUID.randomUUID().toString(), InetAddress.localHost)
    }

    @Override
    Publisher<HttpStatus> pass(String checkId, Optional<String> note) {
        passingReports.add(checkId)
        String service = nameFromCheck(checkId)
        checks.get(service).setStatus(Check.Status.PASSING.name().toLowerCase())

        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> warn(String checkId, Optional<String> note) {
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> fail(String checkId, Optional<String> note) {
        String service = nameFromCheck(checkId)
        checks.get(service).setStatus(Check.Status.CRITICAL.name().toLowerCase())
        return Publishers.just(HttpStatus.OK)
    }

    private String nameFromCheck(String checkId) {
        String service = checkId.substring("service:".length())
        service = service.substring(0, service.indexOf(':'))
        service
    }

    @Override
    Publisher<String> status() {
        return Publishers.just("localhost")
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
        lastNewEntry = entry
        def service = entry.getName()
        services.put(service, new ServiceEntry(entry))
        checks.computeIfAbsent(service, { String key -> new MockCheckEntry(service)})
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> deregister(@NotNull String service) {
        checks.remove(service)
        def s = services.find { it.value.ID.isPresent() ? it.value.ID.get().equals(service) : it.value.name == service }
        if(s) {
            services.remove(s.value.name)
        }
        else {
            services.remove(service)
        }
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<Map<String, ServiceEntry>> getServices() {
        return Publishers.just(services)
    }

    @Override
    Publisher<List<HealthEntry>> getHealthyServices(
            @NotNull String service, Optional<Boolean> passing, Optional<String> tag, Optional<String> dc) {
        ServiceEntry serviceEntry = services.get(service)
        List<HealthEntry> healthEntries = []
        if(serviceEntry != null) {
            def entry = new MockHealthEntry()
            entry.setNode(nodeEntry)
            entry.setService(serviceEntry)
            entry.setChecks([checks.computeIfAbsent(service, { String key -> new MockCheckEntry(service)})])
            healthEntries.add(entry)
        }
        return Publishers.just(healthEntries)
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
