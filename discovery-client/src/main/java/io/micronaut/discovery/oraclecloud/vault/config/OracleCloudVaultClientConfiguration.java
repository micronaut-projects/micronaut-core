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
package io.micronaut.discovery.oraclecloud.vault.config;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;

import java.util.List;

/**
 *  OracleCloudVault Client.
 *
 *  @author toddsharp
 *  @since 2.0.0
 */
@ConfigurationProperties(OracleCloudVaultClientConfiguration.PREFIX)
@BootstrapContextCompatible
public class OracleCloudVaultClientConfiguration {

    public static final String PREFIX = "oraclecloud.vault";
    private static final Boolean USE_INSTANCE_PRINCIPAL = false;
    private static final String PATH_TO_OCI_CONFIG = "~/.oci/config";
    private static final String DEFAULT_PROFILE = "DEFAULT";

    private final OracleCloudVaultClientDiscoveryConfiguration oracleCloudVaultClientDiscoveryConfiguration = new OracleCloudVaultClientDiscoveryConfiguration();

    private List<OracleCloudVault> vaults;
    private boolean useInstancePrincipal = USE_INSTANCE_PRINCIPAL;
    private String pathToConfig = PATH_TO_OCI_CONFIG;
    private String profile = DEFAULT_PROFILE;
    private String region;

    /**
     * @return The discovery service configuration
     */
    public OracleCloudVaultClientDiscoveryConfiguration getDiscoveryConfiguration() {
        return oracleCloudVaultClientDiscoveryConfiguration;
    }

    /**
     * A list of {@link OracleCloudVault} objects that contain secrets that will be retrieved, decoded and set into your application as config variables.
     *
     * @return A list of Vaults to retrieve
     */
    public List<OracleCloudVault> getVaults() {
        return vaults;
    }

    /**
     * A list of {@link OracleCloudVault} objects that contain secrets that will be retrieved, decoded and set into your application as config variables.
     *
     * @param vaults A list of Vaults
     */
    public void setVaults(List<OracleCloudVault> vaults) {
        this.vaults = vaults;
    }

    /**
     * Whether or not the configuration client should use <a href="https://docs.cloud.oracle.com/en-us/iaas/Content/Identity/Tasks/callingservicesfrominstances.htm">Instance Principal Authentication</a> to interact with the SDK. If this value is false, you must include a path to an OCI Config file to use Config File based auth.
     *
     * @return Whether or not we should use instance principal authentication
     */
    public boolean isUseInstancePrincipal() {
        return useInstancePrincipal;
    }

    /**
     * Whether or not the configuration client should use <a href="https://docs.cloud.oracle.com/en-us/iaas/Content/Identity/Tasks/callingservicesfrominstances.htm">Instance Principal Authentication</a> to interact with the SDK. If this value is false, you must include a path to an OCI Config file to use Config File based auth.
     *
     * @param useInstancePrincipal Should we use instance principal authentication
     */
    public void setUseInstancePrincipal(boolean useInstancePrincipal) {
        this.useInstancePrincipal = useInstancePrincipal;
    }

    /**
     * If you are not using Instance Principal auth then you must pass the path to a valid OCI config file. Default value {@value #PATH_TO_OCI_CONFIG}.
     *
     * @return The path to the OCI config file (if not using instance principal auth)
     */
    public String getPathToConfig() {
        return pathToConfig;
    }

    /**
     * If you are not using Instance Principal auth then you must pass the path to a valid OCI config file. Default value {@value #PATH_TO_OCI_CONFIG}.
     *
     * @param pathToConfig The path to the OCI config file
     */
    public void setPathToConfig(String pathToConfig) {
        this.pathToConfig = pathToConfig;
    }

    /**
     * Which profile in the config file should be used? Default value {@value #DEFAULT_PROFILE}.
     *
     * @return Which profile in the OCI config file to use (default: DEFAULT)
     */
    public String getProfile() {
        return profile;
    }

    /**
     * Which profile in the config file should be used? Default value {@value #DEFAULT_PROFILE}.
     *
     * @param profile Which profile in the OCI config file to use (default: DEFAULT)
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * The OCI region Ex: 'US-PHOENIX-1'.
     *
     * @return Which region in the Oracle Cloud should the client work in?
     */
    public String getRegion() {
        return region;
    }

    /**
     * The OCI region Ex: 'US-PHOENIX-1'.
     *
     * @param region Which region in the Oracle Cloud should the client work in?
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * An Oracle Cloud Vault
     */
    @EachProperty(value = "vaults", list = true)
    @BootstrapContextCompatible
    public static class OracleCloudVault {
        private String ocid;
        private String compartmentOcid;

        /**
         * The OCID of the vault that contains secrets that will be retrieved, decoded and set as config vars.
         *
         * @return The OCID of the vault.
         */
        public String getOcid() {
            return ocid;
        }

        /**
         * Sets the OCID of the vault that contains secrets that will be retrieved, decoded and set as config vars
         *
         * @param ocid the ocid of the vault
         */
        public void setOcid(String ocid) {
            this.ocid = ocid;
        }

        /**
         * The compartment OCID where the vault resides.
         *
         * @return The compartment OCID.
         */
        public String getCompartmentOcid() {
            return compartmentOcid;
        }

        /**
         * Sets the compartment OCID where the vault resides.
         *
         * @param compartmentOcid The compartment OCID
         */
        public void setCompartmentOcid(String compartmentOcid) {
            this.compartmentOcid = compartmentOcid;
        }

        @Override
        public String toString() {
            return "OracleCloudVault{" +
                    "ocid='" + ocid + '\'' +
                    ", compartmentOcid='" + compartmentOcid + '\'' +
                    '}';
        }
    }

    /**
     * The Discovery Configuration class for Oracle Cloud Vault.
     */
    @ConfigurationProperties(ConfigDiscoveryConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class OracleCloudVaultClientDiscoveryConfiguration extends ConfigDiscoveryConfiguration {
        public static final String PREFIX = OracleCloudVaultClientConfiguration.PREFIX + "." + ConfigDiscoveryConfiguration.PREFIX;
    }
}
