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
package io.micronaut.discovery.vault.config;

import io.micronaut.http.annotation.Header;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A contract for an HTTP client to read configuration from Vault.
 *
 * @param <T> The body type
 * @author James Kleeh
 * @since 1.2.0
 */
public interface VaultConfigHttpClient<T extends AbstractVaultResponse> {

    /**
     * @return The client description
     */
    String getDescription();

    /**
     * Read configuration from Vault.
     *
     * @param token The vault token
     * @param backend The secret engine name
     * @param vaultKey The vault key
     * @return A publisher of the response body
     */
    Publisher<T> readConfigurationValues(@NonNull @Header("X-Vault-Token") String token,
                                         @NonNull String backend,
                                         @NonNull String vaultKey);

}
