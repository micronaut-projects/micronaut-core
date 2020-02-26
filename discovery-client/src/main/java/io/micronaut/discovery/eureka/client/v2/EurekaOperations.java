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
package io.micronaut.discovery.eureka.client.v2;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.retry.annotation.Retryable;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * API operations for Eureka. See https://github.com/Netflix/eureka/wiki/Eureka-REST-operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface EurekaOperations {

    /**
     * Registers a new {@link InstanceInfo} with the Eureka server.
     *
     * @param appId    The application id
     * @param instance The instance
     * @return A status of {@link io.micronaut.http.HttpStatus#NO_CONTENT} on success
     */
    @Post(uri = "/apps/{appId}", single = true)
    @Retryable(
        attempts = AbstractEurekaClient.EXPR_EUREKA_REGISTRATION_RETRY_COUNT,
        delay = AbstractEurekaClient.EXPR_EUREKA_REGISTRATION_RETRY_DELAY
    )
    Publisher<HttpStatus> register(@NotBlank String appId, @Valid @NotNull @Body InstanceInfo instance);

    /**
     * De-registers a {@link InstanceInfo} with the Eureka server.
     *
     * @param appId      The application id
     * @param instanceId The instance id (this is the value of {@link InstanceInfo#getId()})
     * @return A status of {@link io.micronaut.http.HttpStatus#OK} on success
     */
    @Delete(uri = "/apps/{appId}/{instanceId}", single = true)
    @Retryable(
        attempts = AbstractEurekaClient.EXPR_EUREKA_REGISTRATION_RETRY_COUNT,
        delay = AbstractEurekaClient.EXPR_EUREKA_REGISTRATION_RETRY_DELAY
    )
    Publisher<HttpStatus> deregister(@NotBlank String appId, @NotBlank String instanceId);

    /**
     * Obtain a {@link ApplicationInfo} for the given app id.
     *
     * @param appId The app id
     * @return The {@link ApplicationInfo} instance
     */
    @Get(uri = "/apps/{appId}", single = true)
    Publisher<ApplicationInfo> getApplicationInfo(@NotBlank String appId);

    /**
     * Obtain a {@link InstanceInfo} for the given app id.
     *
     * @param appId      The app id
     * @param instanceId The instance id (this is the value of {@link InstanceInfo#getId()})
     * @return The {@link InstanceInfo} instance
     */
    @Get(uri = "/apps/{appId}/{instanceId}", single = true)
    Publisher<InstanceInfo> getInstanceInfo(@NotBlank String appId, @NotBlank String instanceId);

    /**
     * Obtain all of the {@link ApplicationInfo} registered with Eureka.
     *
     * @return The {@link ApplicationInfo} instances
     */
    Publisher<List<ApplicationInfo>> getApplicationInfos();

    /**
     * Obtain all of the {@link ApplicationInfo} registered with Eureka under the given VIP address.
     *
     * @param vipAddress The {@link InstanceInfo#vipAddress}
     * @return The {@link ApplicationInfo} instances
     * @see InstanceInfo#vipAddress
     */
    Publisher<List<ApplicationInfo>> getApplicationVips(String vipAddress);

    /**
     * Send an application heartbeat to Eureka.
     *
     * @param appId      The application id
     * @param instanceId The instance id
     * @return A status of {@link io.micronaut.http.HttpStatus#OK} on success
     */
    @Put(uri = "/apps/{appId}/{instanceId}", single = true)
    Publisher<HttpStatus> heartbeat(@NotBlank String appId, @NotBlank String instanceId);

    /**
     * Update the application's status.
     *
     * @param appId      The application id
     * @param instanceId The instance id
     * @param status     The status to use
     * @return A status of {@link io.micronaut.http.HttpStatus#OK} on success
     */
    @Put(uri = "/apps/{appId}/{instanceId}/status?value={status}", single = true)
    Publisher<HttpStatus> updateStatus(@NotBlank String appId, @NotBlank String instanceId, @NotNull InstanceInfo.Status status);

    /**
     * Update application metadata value.
     *
     * @param appId      The application id
     * @param instanceId The instance id
     * @param key        The key to update
     * @param value      The value to update
     * @return A status of {@link io.micronaut.http.HttpStatus#OK} on success
     */
    @Put(uri = "/apps/{appId}/{instanceId}/metadata?{key}={value}", single = true)
    Publisher<HttpStatus> updateMetadata(@NotBlank String appId, @NotBlank String instanceId, @NotBlank String key, @NotBlank String value);
}
