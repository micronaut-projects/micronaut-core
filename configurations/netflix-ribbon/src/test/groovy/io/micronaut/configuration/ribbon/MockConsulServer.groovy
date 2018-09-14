/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.ribbon

import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.consul.client.v1.CatalogEntry
import io.micronaut.discovery.consul.client.v1.ConsulOperations
import io.micronaut.discovery.consul.client.v1.HealthEntry
import io.micronaut.discovery.consul.client.v1.KeyValue
import io.micronaut.discovery.consul.client.v1.NewServiceEntry
import io.micronaut.discovery.consul.client.v1.ServiceEntry
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher

import javax.annotation.Nullable
import javax.validation.constraints.NotNull
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple server that mocks the Consul API
 *
 * @author graemerocher
 * @since 1.0
 */
@Controller("/v1")
class MockConsulServer implements ConsulOperations {

    Map<String, List<ServiceEntry>> services = new ConcurrentHashMap<>()
    final CatalogEntry nodeEntry

    static NewServiceEntry lastNewEntry
    static List<String> passingReports = []

    MockConsulServer(EmbeddedServer embeddedServer) {
        lastNewEntry = null
        passingReports.clear()
        nodeEntry = new CatalogEntry(UUID.randomUUID().toString(), InetAddress.localHost)
    }

    @Override
    Publisher<Boolean> putValue(String key, @Body String value) {
        return Flowable.just(true)
    }

    @Override
    Publisher<List<KeyValue>> readValues(String key) {
        return Flowable.just(Collections.emptyList())
    }

    @Override
    Publisher<List<KeyValue>> readValues(String key,
                                         @Nullable @QueryValue("dc") String datacenter,
                                         @Nullable Boolean raw, @Nullable String seperator) {
        return Flowable.just(Collections.emptyList())
    }

    @Override
    Publisher<HttpStatus> pass(String checkId, @Nullable String note) {
        passingReports.add(checkId)
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> warn(String checkId, @Nullable String note) {
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> fail(String checkId, @Nullable String note) {
        return Publishers.just(HttpStatus.OK)
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
        services.computeIfAbsent(entry.getName(), { String key -> []})
                .add(new ServiceEntry(entry))
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> deregister(@NotNull String service) {
        services.remove(service)
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<Map<String, ServiceEntry>> getServices() {
        return Publishers.just(Collections.emptyMap())
    }

    @Override
    Publisher<List<HealthEntry>> getHealthyServices(
            @NotNull String service, @Nullable Boolean passing, @Nullable String tag, @Nullable String dc) {
        List<ServiceEntry> serviceEntry = services.get(service)
        List<HealthEntry> healthEntries = []
        for(se in serviceEntry) {
            def entry = new HealthEntry()
            entry.setNode(nodeEntry)
            entry.setService(se)
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
        return Publishers.just(services.collectEntries { String key, List<ServiceEntry> entry ->
            return [(key): (entry*.tags) as List<String>]
        })
    }
}

