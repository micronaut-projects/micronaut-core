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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

import javax.inject.Inject;

/**
 *  A {@link HttpClientConfiguration} for Vault Client.
 *
 *  @author thiagolocatelli
 *  @since 1.2.0
 */
@ConfigurationProperties(VaultClientConfiguration.PREFIX)
@BootstrapContextCompatible
public class VaultClientConfiguration extends HttpClientConfiguration {

    public static final String PREFIX = "vault.client";

    /**
     * Vault Server Endpoint.
     */
    public static final String VAULT_CLIENT_CONFIG_ENDPOINT = "${" + VaultClientConfiguration.PREFIX + ".uri}";

    private static final String DEFAULT_URI = "http://locahost:8200";
    private static final Boolean DEFAULT_FAIL_FAST = false;
    private static final String DEFAULT_SECRET_ENGINE = "secret";
    private static final VaultKvVersion DEFAULT_KV_VERSION = VaultKvVersion.V2;

    /**
     * Vault Secret Engine versions.
     */
    public enum VaultKvVersion { V1, V2 }

    private final VaultClientConnectionPoolConfiguration vaultClientConnectionPoolConfiguration;
    private final VaultClientDiscoveryConfiguration vaultClientDiscoveryConfiguration = new VaultClientDiscoveryConfiguration();

    private String uri = DEFAULT_URI;
    private String token;
    private VaultKvVersion kvVersion = DEFAULT_KV_VERSION;
    private String secretEngineName = DEFAULT_SECRET_ENGINE;
    private boolean failFast = DEFAULT_FAIL_FAST;

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
     * Set the Vault Server Uri. Default value ({@value #DEFAULT_URI}).
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
     * @return The Vault Secret engine version
     */
    public VaultKvVersion getKvVersion() {
        return kvVersion;
    }

    /**
     * Set the version of the Vault Secret engine. V1 or V2. Default value (V2).
     *
     * @param kvVersion The version of the Vault Secret engine
     */
    public void setKvVersion(VaultKvVersion kvVersion) {
        this.kvVersion = kvVersion;
    }

    /**
     * @return The Vault Secret engine name
     */
    public String getSecretEngineName() {
        return secretEngineName;
    }

    /**
     * Set the name of the Vault Secret engine name. Default value ({@value #DEFAULT_SECRET_ENGINE}).
     *
     * @param secretEngineName Vault Secret engine name
     */
    public void setSecretEngineName(String secretEngineName) {
        this.secretEngineName = secretEngineName;
    }

    /**
     * @return Flag to indicate that failure to connect to HashiCorp Vault is fatal (default false).
     */
    public boolean isFailFast() {
        return failFast;
    }

    /**
     * If set to true an exception will be thrown if configuration is not found
     * for the application or any of its environments. Default value ({@value #DEFAULT_FAIL_FAST}).
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
    public static class VaultClientDiscoveryConfiguration extends ConfigDiscoveryConfiguration {

        public static final String PREFIX = VaultClientConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;
    }

}
