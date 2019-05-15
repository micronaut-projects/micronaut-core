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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.vault.VaultClientConfiguration;
import io.micronaut.discovery.vault.config.client.v1.condition.RequiresVaultClientConfigV1;
import io.micronaut.http.client.annotation.Client;

/**
 *  A non-blocking HTTP client for Vault - KV v2.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@Client(value = VaultClientConfiguration.VAULT_CLIENT_CONFIG_ENDPOINT, configuration = VaultClientConfiguration.class)
@Requires(beans = VaultClientConfiguration.class)
@RequiresVaultClientConfigV1
@BootstrapContextCompatible
public interface VaultConfigHttpClientV1 extends VaultConfigHttpClientV1Operations {

    /**
     * Vault Http Client description.
     */
    String CLIENT_DESCRIPTION = "vault-config-client-v1";

}
