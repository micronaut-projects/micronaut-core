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

package io.micronaut.discovery.vault;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.vault.condition.RequiresVaultClientConfig;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Inject;

/**
 *  A {@link HttpClientConfiguration} for Vault Client.
 *
 *  @author thiagolocatelli
 *  @author graemerocher
 *  @since 1.1.1
 */
@RequiresVaultClientConfig
@ConfigurationProperties(VaultClientConstants.PREFIX)
@BootstrapContextCompatible
public class VaultClientConfiguration extends HttpClientConfiguration {

    /**
     * Vault Server Endpoint.
     */
    public static final String VAULT_CLIENT_CONFIG_ENDPOINT = "${" + VaultClientConstants.PREFIX + ".uri}";

    /**
     * Vault Backend Secret Engine versions.
     */
    public enum VaultKvVersion { V1, V2 };

    private final VaultClientConnectionPoolConfiguration vaultClientConnectionPoolConfiguration;
    private final VaultClientDiscoveryConfiguration vaultClientDiscoveryConfiguration = new VaultClientDiscoveryConfiguration();

    private String uri = "http://locahost:8200";
    private String token;
    private VaultKvVersion kvVersion = VaultKvVersion.V2;
    private String backend = "secret";
    private boolean failFast;

    /**
     * @param vaultClientConnectionPoolConfiguration Vault Client Connection Pool Configuration
     * @param applicationConfiguration Application Configuration
     */
    @Inject
    public VaultClientConfiguration(VaultClientConnectionPoolConfiguration vaultClientConnectionPoolConfiguration, ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
        this.vaultClientConnectionPoolConfiguration = vaultClientConnectionPoolConfiguration;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return vaultClientConnectionPoolConfiguration;
    }

    /**
     * @return The discovery service configuration
     */
    public VaultClientDiscoveryConfiguration getDiscoveryConfiguration() {
        return vaultClientDiscoveryConfiguration;
    }

    /**
     * @return The Vault Server Uri
     */
    public String getUri() {
        return uri;
    }

    /**
     * Set the Vault Server Uri.
     *
     * @param uri Vault Server Uri
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * @return The Vault authentication token
     */
    public String getToken() {
        return token;
    }

    /**
     * Set the Vault authentication token.
     *
     * @param token Vault authentication token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * @return The Backend Secret engine version
     */
    public VaultKvVersion getKvVersion() {
        return kvVersion;
    }

    /**
     * Set the version of the Backend Secret engine.
     *
     * @param kvVersion The version of the Backend Secret engine
     */
    public void setKvVersion(VaultKvVersion kvVersion) {
        this.kvVersion = kvVersion;
    }

    /**
     * @return The Backend Secret engine name
     */
    public String getBackend() {
        return backend;
    }

    /**
     * Set the name of the Backend Secret engine.
     *
     * @param backend Backend Secret engine name
     */
    public void setBackend(String backend) {
        this.backend = backend;
    }

    /**
     * @return Flag to indicate that failure to connect to HashiCorp Vault is fatal (default false).
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * Set flag to indicate that failure to connect to HashiCorp Vault is fatal.
     *
     * @param failFast Flag to fail fast
     */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * The Http Pool Connection Configuration class for Vault.
     */
    @ConfigurationProperties(ConnectionPoolConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class VaultClientConnectionPoolConfiguration extends ConnectionPoolConfiguration { }

    /**
     * The Discovery Configuration class for Vault.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class VaultClientDiscoveryConfiguration extends ConfigDiscoveryConfiguration { }

}
