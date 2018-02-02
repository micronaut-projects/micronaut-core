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
package org.particleframework.discovery.eureka

import org.particleframework.core.async.publisher.Publishers
import org.particleframework.discovery.eureka.client.v2.ApplicationInfo
import org.particleframework.discovery.eureka.client.v2.EurekaOperations
import org.particleframework.discovery.eureka.client.v2.InstanceInfo
import org.particleframework.discovery.eureka.client.v2.MockApplicationInfo
import org.particleframework.discovery.eureka.client.v2.MockApplicationInfos
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.reactivestreams.Publisher

import javax.inject.Singleton
import javax.validation.Valid
import javax.validation.constraints.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller('/eureka')
@Singleton
class MockEurekaServer implements EurekaOperations{
    public static Map<String, Map<String, Boolean>> heartbeats = new ConcurrentHashMap<>()
    public static Map<String, Map<String, InstanceInfo>> instances = new ConcurrentHashMap<>()
    @Override
    Publisher<HttpStatus> register(@NotBlank String appId, @Valid @NotNull @Body InstanceInfo instance) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                 .put(instance.instanceId, instance)
        return Publishers.just(HttpStatus.NO_CONTENT)
    }

    @Override
    Publisher<HttpStatus> deregister(@NotBlank String appId, @NotBlank String instanceId) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                 .remove(instanceId)
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
    Publisher<MockApplicationInfos> getApplicationInfosInternal() {
        return Publishers.just(new MockApplicationInfos(instances.collect { it ->
            new MockApplicationInfo(it.key, it.value.values() as List<InstanceInfo>)
        } as List<ApplicationInfo>))
    }

    @Override
    Publisher<List<ApplicationInfo>> getApplicationInfos() {
        // no-op... never called
    }

    @Override
    Publisher<HttpStatus> heartbeat(@NotBlank String appId, @NotBlank String instanceId) {
        heartbeats.computeIfAbsent(appId, { String id -> [:]}).put(instanceId, true)
        return Publishers.just(HttpStatus.OK)
    }

    @Override
    Publisher<HttpStatus> updateStatus(
            @NotBlank String appId, @NotBlank String instanceId, @NotNull InstanceInfo.Status status) {
        instances.computeIfAbsent(appId, { String id -> new ConcurrentHashMap<>()})
                .get(instanceId)?.status = status
        return Publishers.just(HttpStatus.OK)
    }
}
