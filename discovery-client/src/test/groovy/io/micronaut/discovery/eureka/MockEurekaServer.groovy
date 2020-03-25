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
package io.micronaut.discovery.eureka

import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.eureka.client.v2.*
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import org.reactivestreams.Publisher

import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.util.concurrent.ConcurrentHashMap

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller(EurekaConfiguration.CONTEXT_PATH_PLACEHOLDER)
@Requires(property = MockEurekaServer.ENABLED)
class MockEurekaServer implements EurekaOperations{
    public static Map<String, Map<String, Boolean>> heartbeats = new ConcurrentHashMap<>()
    public static Map<String, Map<String, InstanceInfo>> instances = new ConcurrentHashMap<>()
    public static final String ENABLED = 'enable.mock.eureka'

    @Override
    Publisher<HttpStatus> register(@NotBlank String appId, @Valid @NotNull @Body InstanceInfo instance) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                 .put(instance.instanceId, instance)
        return Publishers.just(HttpStatus.NO_CONTENT)
    }

    @Override
    Publisher<HttpStatus> deregister(@NotBlank String appId, @NotBlank String instanceId) {
        def instances = instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>() })
        instances.remove(instanceId)
        if(instances.isEmpty()) {
            instances.remove(appId)
        }
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<ApplicationInfo> getApplicationInfo(@NotBlank String appId) {
        List<InstanceInfo> infos = instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                 .values() as List<InstanceInfo>
        return Publishers.just(
                new MockApplicationInfo(appId, infos)
        )
    }

    @Override
    Publisher<InstanceInfo> getInstanceInfo(@NotBlank String appId, @NotBlank String instanceId) {
        return Publishers.just(
                instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                        .get(instanceId)
        )
    }

    @Get('/apps')
    @Produces(single = true)
    Publisher<MockApplicationInfos> getApplicationInfosInternal() {
        return Publishers.just(new MockApplicationInfos(instances.findAll { !it.value.isEmpty() }.collect { it ->
            new MockApplicationInfo(it.key, it.value.values() as List<InstanceInfo>)
        } as List<ApplicationInfo>))
    }

    @Override
    Publisher<List<ApplicationInfo>> getApplicationInfos() {
        // no-op... never called
    }

    @Override
    Publisher<List<ApplicationInfo>> getApplicationVips(String vipAddress) {
        // no-op... never called
    }

    @Get('/vips/{vipAddress}')
    @Produces(single = true)
    Publisher<MockApplicationInfos> getApplicationVipsInternal(String vipAddress) {
        // this logic is wrong, i know.. we just test the call
        return Publishers.just(new MockApplicationInfos(instances.findAll { !it.value.isEmpty() }.collect { it ->
            new MockApplicationInfo(it.key, it.value.values() as List<InstanceInfo>)
        } as List<ApplicationInfo>))
    }

    @Override
    Publisher<HttpStatus> heartbeat(@NotBlank String appId, @NotBlank String instanceId) {
        heartbeats.computeIfAbsent(appId, { String id -> [:]}).put(instanceId, true)
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    @Put("/apps/{appId}/{instanceId}/status")
    Publisher<HttpStatus> updateStatus(
            @NotBlank String appId, @NotBlank String instanceId, @NotNull @QueryValue("value") InstanceInfo.Status status) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                .get(instanceId)?.status = status
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> updateMetadata(
            @NotBlank String appId, @NotBlank String instanceId, @NotBlank String key, @NotBlank String value) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                .get(instanceId)?.metadata?.put(key, value)
        return Publishers.just(HttpStatus.OK)
    }
}
