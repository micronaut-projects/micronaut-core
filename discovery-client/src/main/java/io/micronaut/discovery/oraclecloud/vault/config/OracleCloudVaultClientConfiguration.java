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
     * @return A list of Vaults to retrieve
     */
    public List<OracleCloudVault> getVaults() {
        return vaults;
    }

    /**
     * @param vaults A list of Vaults
     */
    public void setVaults(List<OracleCloudVault> vaults) {
        this.vaults = vaults;
    }

    /**
     * @return Whether or not we should use instance principal authentication
     */
    public boolean isUseInstancePrincipal() {
        return useInstancePrincipal;
    }

    /**
     * @param useInstancePrincipal Should we use instance principal authentication
     */
    public void setUseInstancePrincipal(boolean useInstancePrincipal) {
        this.useInstancePrincipal = useInstancePrincipal;
    }

    /**
     * @return The path to the OCI config file (if not using instance principal auth)
     */
    public String getPathToConfig() {
        return pathToConfig;
    }

    /**
     * @param pathToConfig The path to the OCI config file
     */
    public void setPathToConfig(String pathToConfig) {
        this.pathToConfig = pathToConfig;
    }

    /**
     * @return Which profile in the OCI config file to use (default: DEFAULT)
     */
    public String getProfile() {
        return profile;
    }

    /**
     * @param profile Which profile in the OCI config file to use (default: DEFAULT)
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * @return Which region in the Oracle Cloud should the client work in?
     */
    public String getRegion() {
        return region;
    }

    /**
     * @param region Which region in the Oracle Cloud should the client work in?
     */
    public void setRegion(String region) {
        this.region = region;
    }


    @EachProperty(value = "vaults", list = true)
    public static class OracleCloudVault {
        private String ocid;
        private String compartmentOcid;

        public OracleCloudVault(String ocid, String compartmentOcid) {
            this.ocid = ocid;
            this.compartmentOcid = compartmentOcid;
        }

        public String getOcid() {
            return ocid;
        }

        public void setOcid(String ocid) {
            this.ocid = ocid;
        }

        public String getCompartmentOcid() {
            return compartmentOcid;
        }

        public void setCompartmentOcid(String compartmentOcid) {
            this.compartmentOcid = compartmentOcid;
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
