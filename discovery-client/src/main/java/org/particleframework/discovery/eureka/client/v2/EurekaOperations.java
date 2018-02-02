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
package org.particleframework.discovery.eureka.client.v2;

import org.particleframework.http.HttpStatus;
import org.particleframework.http.annotation.*;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * API operations for Eureka. See https://github.com/Netflix/eureka/wiki/Eureka-REST-operations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface EurekaOperations {

    /**
     * Registers a new {@link InstanceInfo} with the Eureka server
     *
     * @param appId The application id
     * @param instance The instance
     * @return A status of {@link HttpStatus#NO_CONTENT} on success
     */
    @Post("/apps/{appId}")
    Publisher<HttpStatus> register(@NotBlank String appId, @Valid @NotNull @Body InstanceInfo instance);

    /**
     * De-registers a {@link InstanceInfo} with the Eureka server
     *
     * @param appId The application id
     * @param instanceId The instance id (this is the value of {@link InstanceInfo#getId()})
     * @return A status of {@link HttpStatus#OK} on success
     */
    @Delete("/apps/{appId}/{instanceId}")
    Publisher<HttpStatus> deregister(@NotBlank String appId, @NotBlank String instanceId);

    /**
     * Obtain a {@link ApplicationInfo} for the given app id
     *
     * @param appId The app id
     * @return The {@link ApplicationInfo} instance
     */
    @Get("/apps/{appId}")
    Publisher<ApplicationInfo> getApplicationInfo(@NotBlank String appId);

    /**
     * Obtain a {@link InstanceInfo} for the given app id
     *
     * @param appId The app id
     * @param instanceId The instance id (this is the value of {@link InstanceInfo#getId()})
     * @return The {@link InstanceInfo} instance
     */
    @Get("/apps/{appId}/{instanceId}")
    Publisher<InstanceInfo> getInstanceInfo(@NotBlank String appId, @NotBlank String instanceId);


    /**
     * Obtain all of the {@link ApplicationInfo} registered with Eureka
     *
     * @return The {@link ApplicationInfo} instance
     */
    Publisher<List<ApplicationInfo>> getApplicationInfos();
    /**
     * Send an application heartbeat to Eureka
     *
     * @param appId The application id
     * @param instanceId The instance id
     * @return A status of {@link HttpStatus#OK} on success
     */
    @Put("/apps/{appId}/{instanceId}")
    Publisher<HttpStatus> heartbeat(@NotBlank String appId, @NotBlank String instanceId);

    /**
     * Update the application's status
     * @param status The status to use
     * @return A status of {@link HttpStatus#OK} on success
     */
    @Put("/apps/{appId}/{instanceId}/status?value={status}")
    Publisher<HttpStatus> updateStatus(@NotBlank String appId, @NotBlank String instanceId, @NotNull InstanceInfo.Status status);
}
