/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.retry.annotation.Retryable;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

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
    @Put(value = "/kv/{+key}", processes = MediaType.TEXT_PLAIN, single = true)
    Publisher<Boolean> putValue(String key, @Body String value);

    /**
     * Reads a Key from Consul. See https://www.consul.io/api/kv.html.
     *
     * @param key The key to read
     * @return A {@link Publisher} that emits a list of {@link KeyValue}
     */
    @Get(uri = "/kv/{+key}?recurse", single = true)
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
    @Get(uri = "/kv/{+key}?recurse=true{&dc}{&raw}{&seperator}", single = true)
    @Retryable(
        attempts = AbstractConsulClient.EXPR_CONSUL_CONFIG_RETRY_COUNT,
        delay = AbstractConsulClient.EXPR_CONSUL_CONFIG_RETRY_DELAY
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
     * @return An {@link io.micronaut.http.HttpStatus} of {@link io.micronaut.http.HttpStatus#OK} if all is well
     */
    @Put("/agent/check/pass/{checkId}{?note}")
    @Retryable(
            attempts = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_COUNT,
            delay = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_DELAY
    )
    Publisher<HttpStatus> pass(String checkId, @Nullable String note);

    /**
     * Warn the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @param note    An optional note
     * @return An {@link io.micronaut.http.HttpStatus} of {@link io.micronaut.http.HttpStatus#OK} if all is well
     */
    @Put("/agent/check/warn/{checkId}{?note}")
    Publisher<HttpStatus> warn(String checkId, @Nullable String note);

    /**
     * Fail the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @param note    An optional note
     * @return An {@link io.micronaut.http.HttpStatus} of {@link io.micronaut.http.HttpStatus#OK} if all is well
     */
    @Put("/agent/check/fail/{checkId}{?note}")
    @Retryable(
            attempts = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_COUNT,
            delay = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_DELAY
    )
    Publisher<HttpStatus> fail(String checkId, @Nullable String note);

    /**
     * @return The current leader address
     */
    @Get(uri = "/status/leader", single = true)
    @Retryable
    Publisher<String> status();

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param entry The entry to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put(uri = "/catalog/register", single = true)
    Publisher<Boolean> register(@NotNull @Body CatalogEntry entry);

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param entry The entry to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put(uri = "/catalog/deregister", single = true)
    Publisher<Boolean> deregister(@NotNull @Body CatalogEntry entry);

    /**
     * Register a new {@link CatalogEntry}. See https://www.consul.io/api/catalog.html.
     *
     * @param entry The entry to register
     * @return A {@link Publisher} that emits a boolean true if the operation was successful
     */
    @Put("/agent/service/register")
    @Retryable(
        attempts = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_COUNT,
        delay = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_DELAY
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
        attempts = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_COUNT,
        delay = AbstractConsulClient.CONSUL_REGISTRATION_RETRY_DELAY
    )
    Publisher<HttpStatus> deregister(@NotNull String service);

    /**
     * Gets all of the registered services.
     *
     * @return The {@link NewServiceEntry} instances
     */
    @Get(uri = "/agent/services", single = true)
    Publisher<Map<String, ServiceEntry>> getServices();

    /**
     * Returns the members the agent sees in the cluster gossip pool.
     *
     * @return the {@link MemberEntry} instances
     */
    @Get(uri = "/agent/members", single = true)
    Publisher<List<MemberEntry>> getMembers();

    /**
     * Returns the configuration and member information of the local agent.
     *
     * @return the {@link LocalAgentConfiguration} instance
     */
    @Get(uri = "/agent/self", single = true)
    Publisher<LocalAgentConfiguration> getSelf();

    /**
     * Gets the healthy services that are passing health checks.
     *
     * @param service The service
     * @param passing The passing parameter
     * @param tag     The tag
     * @param dc      The dc
     * @return The {@link HealthEntry} instances
     */
    @Get(uri = "/health/service/{service}{?passing,tag,dc}", single = true)
    Publisher<List<HealthEntry>> getHealthyServices(
        @NotNull String service,
        @Nullable Boolean passing,
        @Nullable String tag,
        @Nullable String dc);

    /**
     * Gets all of the registered nodes.
     *
     * @return All the nodes
     */
    @Get(uri = "/catalog/nodes", single = true)
    Publisher<List<CatalogEntry>> getNodes();

    /**
     * Gets all the nodes for the given data center.
     *
     * @param datacenter The data center
     * @return A publisher that emits the nodes
     */
    @Get(uri = "/catalog/nodes?dc={datacenter}", single = true)
    Publisher<List<CatalogEntry>> getNodes(@NotNull String datacenter);

    /**
     * Gets all of the service names and optional tags.
     *
     * @return A Map where the keys are service names and the values are service tags
     */
    @Get(uri = "/catalog/services", single = true)
    Publisher<Map<String, List<String>>> getServiceNames();

    /**
     * Pass the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @return An {@link io.micronaut.http.HttpStatus} of {@link io.micronaut.http.HttpStatus#OK} if all is well
     */
    default Publisher<HttpStatus> pass(String checkId) {
        return pass(checkId, null);
    }

    /**
     * Warn the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @return An {@link io.micronaut.http.HttpStatus} of {@link io.micronaut.http.HttpStatus#OK} if all is well
     */
    default Publisher<HttpStatus> warn(String checkId) {
        return warn(checkId, null);
    }

    /**
     * Fail the TTL check. See https://www.consul.io/api/agent/check.html.
     *
     * @param checkId The check ID
     * @return An {@link io.micronaut.http.HttpStatus} of {@link io.micronaut.http.HttpStatus#OK} if all is well
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
        return getHealthyServices(service, null, null, null);
    }
}
