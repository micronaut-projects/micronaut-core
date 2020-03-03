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
package io.micronaut.discovery.consul

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.StringUtils
import io.micronaut.discovery.consul.client.v1.*
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import javax.annotation.Nullable
import javax.validation.constraints.NotNull
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * A simple server that mocks the Consul API
 *
 * @author graemerocher
 * @since 1.0
 */
@Controller("/v1")
@Requires(property = MockConsulServer.ENABLED)
class MockConsulServer implements ConsulOperations {
    public static final String ENABLED = 'enable.mock.consul'

    Map<String, ServiceEntry> services = new ConcurrentHashMap<>()
    Map<String, MockCheckEntry> checks = new ConcurrentHashMap<>()

    Map<String, List<KeyValue>> keyvalues = new ConcurrentHashMap<>()

    final CatalogEntry nodeEntry

    static Map<String, NewServiceEntry> newEntries
    static List<String> passingReports = []

    final MemberEntry agent = new MemberEntry().tap {
        name = "localhost"
        address = InetAddress.localHost
        port = 8301
        status = 1
    }

    MockConsulServer(EmbeddedServer embeddedServer) {
        newEntries = [:]
        passingReports.clear()
        nodeEntry = new CatalogEntry(UUID.randomUUID().toString(), InetAddress.localHost)
    }

    void reset() {
        services.clear()
        checks.clear()
        passingReports.clear()
        newEntries = [:]
    }

    @Override
    Publisher<Boolean> putValue(String key, @Body String value) {
        // make sure it isn't a folder
        key = URLDecoder.decode(key, "UTF-8")
        if(!key.endsWith("/") && StringUtils.hasText(value)) {
            int i = key.lastIndexOf('/')
            String folder = key
            if(i > -1) {
                folder = key.substring(0, i)
            }
            List<KeyValue> list = keyvalues.computeIfAbsent(folder, { String k -> []})
            list.add(new KeyValue(key, Base64.getEncoder().encodeToString(value.bytes)))
        }
        return Flowable.just(true)
    }

    @Override
    @Get("/kv/{+key}")
    Mono<List<KeyValue>> readValues(String key) {
        key = URLDecoder.decode(key, "UTF-8")
        Map<String, List<KeyValue>> found = keyvalues.findAll { entry -> entry.key.startsWith(key)}
        if(found) {
            return Mono.just(found.values().stream().flatMap({ values -> values.stream() })
                                   .collect(Collectors.toList()))
        }
        else {
            int i = key.lastIndexOf('/')
            if(i > -1) {
                String prefix = key.substring(0,i)

                List<KeyValue> values = keyvalues.get(prefix)
                if(values) {
                    return Mono.just(values.findAll({it.key.startsWith(key)}))
                }
            }
        }
        return Mono.just(Collections.emptyList())
    }

    @Override
    Mono<List<KeyValue>> readValues(String key,
                                    @Nullable @QueryValue("dc") String datacenter,
                                    @Nullable Boolean raw, @Nullable String seperator) {
        return readValues(key)
    }

    @Override
    Publisher<HttpStatus> pass(String checkId, @Nullable String note) {
        passingReports.add(checkId)
        String service = nameFromCheck(checkId)
        checks.get(service).setStatus(Check.Status.PASSING.name().toLowerCase())

        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> warn(String checkId, @Nullable String  note) {
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> fail(String checkId, @Nullable String  note) {
        String service = nameFromCheck(checkId)
        checks.get(service)?.setStatus(Check.Status.CRITICAL.name().toLowerCase())
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
        def service = entry.getName()
        newEntries.put(service, entry)
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
            @NotNull String service, @Nullable Boolean passing, @Nullable String tag, @Nullable String dc) {
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

    @Override
    Publisher<List<MemberEntry>> getMembers() {
        return Publishers.just([agent])
    }

    @Override
    Publisher<LocalAgentConfiguration> getSelf() {
        return Publishers.just(new LocalAgentConfiguration().tap {
            configuration = [
                Datacenter: 'dc1',
                NodeName: 'foobar',
                NodeId: '9d754d17-d864-b1d3-e758-f3fe25a9874f'
            ]
            member = agent
            metadata = [ "os_version": "ubuntu_16.04" ]
        })
    }
}
