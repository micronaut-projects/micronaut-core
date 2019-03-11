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
package io.micronaut.discovery.spring.config.client;

import io.micronaut.discovery.spring.SpringCloudConfigConfiguration;
import io.micronaut.discovery.spring.config.client.response.ConfigServerResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.retry.annotation.Retryable;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * API operations for Spring Cloud Config Client.
 *
 * @author Thiago Locatelli
 * @author graemerocher
 * @since 1.1.0
 */
public interface SpringCloudConfigOperations {

    /**
     * Reads a Key from Consul. See https://www.consul.io/api/kv.html.
     *
     * @param applicationName   The key
     * @param profiles          The data center
     * @return A {@link Publisher} that emits a list of {@link ConfigServerResponse}
     */
    @Get("/{applicationName}/{profiles}")
    @Produces(single = true)
    @Retryable(
            attempts = "${" + SpringCloudConfigConfiguration.SpringConfigDiscoveryConfiguration.PREFIX + ".retry-count:3}",
            delay = "${" + SpringCloudConfigConfiguration.SpringConfigDiscoveryConfiguration.PREFIX + ".retry-delay:1s}"
    )
    @Nonnull Publisher<ConfigServerResponse> readValues(
            @Nonnull String applicationName,
            @Nullable String profiles);

}
