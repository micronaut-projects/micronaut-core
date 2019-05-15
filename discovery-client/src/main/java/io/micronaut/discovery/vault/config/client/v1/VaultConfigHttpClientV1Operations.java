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

package io.micronaut.discovery.vault.config.client.v1;

import io.micronaut.discovery.vault.VaultClientConfiguration;
import io.micronaut.discovery.vault.config.client.v1.response.VaultResponseV1;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;
import io.micronaut.retry.annotation.Retryable;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;

/**
 * Vault Client KV Version 1 - Operations.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
public interface VaultConfigHttpClientV1Operations {

    /**
     * Reads an application configuration from Spring Config Server.
     *
     * @param token             Vault authentication token
     * @param backend           The name of the secret engine in Vault
     * @param vaultKey          The vault key
     * @return A {@link Publisher} that emits a list of {@link VaultResponseV1}
     */
    @Get("/v1/{backend}/{vaultKey}")
    @Produces(single = true)
    @Retryable(
            attempts = "${" + VaultClientConfiguration.VaultClientConnectionPoolConfiguration.PREFIX + ".retry-count:3}",
            delay = "${" + VaultClientConfiguration.VaultClientConnectionPoolConfiguration.PREFIX + ".retry-delay:1s}"
    )
    @Nonnull
    Publisher<VaultResponseV1> readConfigurationValues(
            @Nonnull @Header("X-Vault-Token") String token,
            @Nonnull String backend,
            @Nonnull String vaultKey);
}
