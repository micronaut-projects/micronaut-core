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
package io.micronaut.discovery.spring.config.client;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.discovery.spring.config.SpringCloudClientConfiguration;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.retry.annotation.Retryable;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A non-blocking HTTP client for Spring Cloud Config Client.
 *
 * @author Thiago Locatelli
 * @since 1.0
 */
@Client(value = SpringCloudClientConfiguration.SPRING_CLOUD_CONFIG_ENDPOINT, configuration = SpringCloudClientConfiguration.class)
@BootstrapContextCompatible
public interface SpringCloudConfigClient {

    String CLIENT_DESCRIPTION = "spring-cloud-config-client";

    /**
     * Reads an application configuration from Spring Config Server.
     *
     * @param applicationName   The application name
     * @param profiles          The active profiles
     * @return A {@link Publisher} that emits a list of {@link ConfigServerResponse}
     */
    @Get("/{applicationName}{/profiles}")
    @Produces(single = true)
    @Retryable(
            attempts = "${" + SpringCloudClientConfiguration.SpringConfigDiscoveryConfiguration.PREFIX + ".retry-count:3}",
            delay = "${" + SpringCloudClientConfiguration.SpringConfigDiscoveryConfiguration.PREFIX + ".retry-delay:1s}"
    )
    Publisher<ConfigServerResponse> readValues(
            @NonNull String applicationName,
            @Nullable String profiles);

    /**
     * Reads a versioned (#label) application configuration from Spring Config Server.
     *
     * @param applicationName   The application name
     * @param profiles          The active profiles
     * @param label             The label
     * @return A {@link Publisher} that emits a list of {@link ConfigServerResponse}
     */
    @Get("/{applicationName}{/profiles}{/label}")
    @Produces(single = true)
    @Retryable(
            attempts = "${" + SpringCloudClientConfiguration.SpringConfigDiscoveryConfiguration.PREFIX + ".retry-count:3}",
            delay = "${" + SpringCloudClientConfiguration.SpringConfigDiscoveryConfiguration.PREFIX + ".retry-delay:1s}"
    )
    Publisher<ConfigServerResponse> readValues(
            @NonNull String applicationName,
            @Nullable String profiles,
            @Nullable String label);

}
