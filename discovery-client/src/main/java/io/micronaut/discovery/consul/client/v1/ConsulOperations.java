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

package io.micronaut.discovery.consul.client.v1;

import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.retry.annotation.Retryable;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API operations for Consul.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ConsulOperations {

    /**
     * Writes a value for the given key to Consul.
     *
     * @param key   The key
     * @param value The value as a String
     * @return A {@link Publisher} that emits a boolean if the operation succeeded
     */
    @Put(value = "/kv/{+key}", consumes = MediaType.TEXT_PLAIN)
    @Produces(value = MediaType.TEXT_PLAIN, single = true)
    Publisher<Boolean> putValue(String key, @Body String value);

    /**
     * Reads a Key from Consul. See https://www.consul.io/api/kv.html.
     *
     * @param key The key to read
     * @return A {@link Publisher} that emits a list of {@link KeyValue}
     */
    @Get("/kv/{+key}?recurse")
    @Produces(single = true)
    Publisher<List<KeyValue>> readValues(String key);

    /**
     * Reads a Key from Consul. See https://www.consul.io/api/kv.html.
     *
     * @param key        The key
     * @param datacenter The data center
     * @param raw        Whether the value should be raw without encoding or metadata
     * @param seperator  The separator to use
     * @return A {@link Publisher} that emits a list of {@link KeyValue}
     */
    @Get("/kv/{+key}?recurse=true{&dc}{&raw}{&seperator}")
    @Produces(single = true)
    @Retryable(
        attempts = "${" + ConsulConfiguration.ConsulConfigDiscoveryConfiguration.PREFIX + ".retryCount:3}",
        delay = "${" + ConsulConfiguration.ConsulConfigDiscoveryConfiguration.PREFIX + ".retryDelay:1s}"
    )
    Publisher<List<KeyValue>> readValues(
        String key,
        @Nullable @QueryValue("dc") String datacenter,
        @Nullable Boolean raw,
        @Nullable String seperator);

    /**
     * Pass the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @param note    An optional note
     * @return An {@link HttpStatus} of {@link HttpStatus#OK} if all is well
     */
    @Put("/agent/check/pass/{checkId}{?note}")
    Publisher<HttpStatus> pass(String checkId, @Nullable String note);

    /**
     * Warn the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @param note    An optional note
     * @return An {@link HttpStatus} of {@link HttpStatus#OK} if all is well
     */
    @Put("/agent/check/warn/{checkId}{?note}")
    Publisher<HttpStatus> warn(String checkId, @Nullable String note);

    /**
     * Fail the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @param note    An optional note
     * @return An {@link HttpStatus} of {@link HttpStatus#OK} if all is well
     */
    @Put("/agent/check/fail/{checkId}{?note}")
    Publisher<HttpStatus> fail(String checkId, @Nullable String note);

    /**
     * @return The current leader address
     */
    @Get("/status/leader")
    @Produces(single = true)
    Publisher<String> status();

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param entry The entry to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put("/catalog/register")
    @Produces(single = true)
    Publisher<Boolean> register(@NotNull @Body CatalogEntry entry);

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param entry The entry to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put("/catalog/deregister")
    @Produces(single = true)
    Publisher<Boolean> deregister(@NotNull @Body CatalogEntry entry);

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param entry The entry to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put("/agent/service/register")
    @Retryable(
        attempts = "${" + ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".retryCount:3}",
        delay = "${" + ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".retryDelay:1s}"
    )
    Publisher<HttpStatus> register(@NotNull @Body NewServiceEntry entry);

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param service The service to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put("/agent/service/deregister/{service}")
    @Retryable(
        attempts = "${" + ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".retryCount:3}",
        delay = "${" + ConsulConfiguration.ConsulRegistrationConfiguration.PREFIX + ".retryDelay:1s}"
    )
    Publisher<HttpStatus> deregister(@NotNull String service);

    /**
     * Gets all of the registered services.
     *
     * @return The {@link NewServiceEntry} instances
     */
    @Get("/agent/services")
    @Produces(single = true)
    Publisher<Map<String, ServiceEntry>> getServices();

    /**
     * Gets the healthy services that are passing health checks.
     *
     * @param service The service
     * @param passing The passing parameter
     * @param tag     The tag
     * @param dc      The dc
     * @return The {@link HealthEntry} instances
     */
    @Get("/health/service/{service}{?passing,tag,dc}")
    @Produces(single = true)
    Publisher<List<HealthEntry>> getHealthyServices(
        @NotNull String service,
        Optional<Boolean> passing,
        Optional<String> tag,
        Optional<String> dc);

    /**
     * Gets all of the registered nodes.
     *
     * @return All the nodes
     */
    @Get("/catalog/nodes")
    @Produces(single = true)
    Publisher<List<CatalogEntry>> getNodes();

    /**
     * Gets all the nodes for the given data center.
     *
     * @param datacenter The data center
     * @return A publisher that emits the nodes
     */
    @Get("/catalog/nodes?dc={datacenter}")
    @Produces(single = true)
    Publisher<List<CatalogEntry>> getNodes(@NotNull String datacenter);

    /**
     * Gets all of the service names and optional tags.
     *
     * @return A Map where the keys are service names and the values are service tags
     */
    @Get("/catalog/services")
    @Produces(single = true)
    Publisher<Map<String, List<String>>> getServiceNames();

    /**
     * Pass the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @return An {@link HttpStatus} of {@link HttpStatus#OK} if all is well
     */
    default Publisher<HttpStatus> pass(String checkId) {
        return pass(checkId, null);
    }

    /**
     * Warn the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @return An {@link HttpStatus} of {@link HttpStatus#OK} if all is well
     */
    default Publisher<HttpStatus> warn(String checkId) {
        return warn(checkId, null);
    }

    /**
     * Fail the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @return An {@link HttpStatus} of {@link HttpStatus#OK} if all is well
     */
    default Publisher<HttpStatus> fail(String checkId) {
        return fail(checkId, null);
    }

    /**
     * Gets service health information. Defaults to return both non-healthy and healthy services.
     *
     * @param service The service
     * @return The {@link HealthEntry} instances
     */
    default Publisher<List<HealthEntry>> getHealthyServices(@NotNull String service) {
        return getHealthyServices(service, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
