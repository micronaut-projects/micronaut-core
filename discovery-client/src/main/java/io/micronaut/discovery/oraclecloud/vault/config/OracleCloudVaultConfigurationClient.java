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

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import com.oracle.bmc.vault.VaultsClient;
import com.oracle.bmc.vault.model.SecretSummary;
import com.oracle.bmc.vault.requests.ListSecretsRequest;
import com.oracle.bmc.vault.responses.ListSecretsResponse;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.codec.binary.Base64;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 *  A {@link ConfigurationClient} for Oracle Cloud Vault Configuration.
 *
 *  @author toddsharp
 *  @since 2.0.0
 */
@Singleton
@Requires(classes = {
        SecretsClient.class,
        VaultsClient.class,
        InstancePrincipalsAuthenticationDetailsProvider.class,
        ConfigFileAuthenticationDetailsProvider.class
})
@BootstrapContextCompatible
public class OracleCloudVaultConfigurationClient implements ConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(OracleCloudVaultConfigurationClient.class);

    private final OracleCloudVaultClientConfiguration oracleCloudVaultClientConfiguration;
    private final ApplicationConfiguration applicationConfiguration;
    private final ExecutorService executorService;
    private final SecretsClient secretsClient;
    private final VaultsClient vaultsClient;

    /**
     * Default Constructor.
     *
     * @param oracleCloudVaultClientConfiguration   Oracle CloudVault Client Configuration
     * @param applicationConfiguration              The application configuration
     * @param executorService                       Executor Service
     * @throws Exception                            If no configuration is provided
     */
    public OracleCloudVaultConfigurationClient(OracleCloudVaultClientConfiguration oracleCloudVaultClientConfiguration,
                                    ApplicationConfiguration applicationConfiguration,
                                    @Named(TaskExecutors.IO) @Nullable ExecutorService executorService) throws Exception {
        this.oracleCloudVaultClientConfiguration = oracleCloudVaultClientConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.executorService = executorService;

        BasicAuthenticationDetailsProvider provider = null;
        if (oracleCloudVaultClientConfiguration.isUseInstancePrincipal()) {
            provider = InstancePrincipalsAuthenticationDetailsProvider.builder().build();
        } else {
            try {
                provider = new ConfigFileAuthenticationDetailsProvider(oracleCloudVaultClientConfiguration.getPathToConfig(), oracleCloudVaultClientConfiguration.getProfile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Region region = Region.fromRegionCodeOrId(oracleCloudVaultClientConfiguration.getRegion());
        if (provider != null) {
            secretsClient = SecretsClient.builder().region(region).build(provider);
            vaultsClient = VaultsClient.builder().region(region).build(provider);
        } else {
            throw new Exception("You must use instance principal auth or config file auth with the Oracle Cloud Vault Client");
        }
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        if (!oracleCloudVaultClientConfiguration.getDiscoveryConfiguration().isEnabled()) {
            return Flowable.empty();
        }

        final String applicationName = applicationConfiguration.getName().orElse(null);
        final Set<String> activeNames = environment.getActiveNames();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Oracle Cloud Vault OCIDs: {}, Using Instance Principals: {}, Path To Config: {}, Profile: {}, Region: {}",
                    oracleCloudVaultClientConfiguration.getVaults(),
                    oracleCloudVaultClientConfiguration.isUseInstancePrincipal(),
                    oracleCloudVaultClientConfiguration.getPathToConfig(),
                    oracleCloudVaultClientConfiguration.getProfile(),
                    oracleCloudVaultClientConfiguration.getRegion());
            LOG.debug("Application name: {}, application profiles: {}", applicationName, activeNames);
        }

        List<Flowable<PropertySource>> propertySources = new ArrayList<>();
        Scheduler scheduler = executorService != null ? Schedulers.from(executorService) : null;

        Map<String, Object> secrets = new HashMap<>();

        for (OracleCloudVaultClientConfiguration.OracleCloudVault vault : oracleCloudVaultClientConfiguration.getVaults()) {
            int retrieved = 0;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Retrieving secrets from Oracle Cloud Vault with OCID: {}", vault.getOcid());
            }
            List<ListSecretsResponse> responses = new ArrayList<>();
            ListSecretsRequest listSecretsRequest = buildRequest(
                    vault.getOcid(),
                    vault.getCompartmentOcid(),
                    null
            );
            ListSecretsResponse listSecretsResponse = vaultsClient.listSecrets(listSecretsRequest);
            responses.add(listSecretsResponse);

            while (listSecretsResponse.getOpcNextPage() != null) {
                listSecretsRequest = buildRequest(
                        vault.getOcid(),
                        vault.getCompartmentOcid(),
                        listSecretsResponse.getOpcNextPage()
                );
                listSecretsResponse = vaultsClient.listSecrets(listSecretsRequest);
                responses.add(listSecretsResponse);
            }

            for (ListSecretsResponse response : responses) {
                retrieved += response.getItems().size();
                response.getItems().forEach((summary) -> {
                    String secretValue = getSecretValue(summary.getId());
                    secrets.put(
                            summary.getSecretName(),
                            secretValue
                    );
                });
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} secrets where retrieved from Oracle Cloud Vault with OCID: {}", retrieved, vault.getOcid());
            }
        }

        Flowable<PropertySource> propertySourceFlowable = Flowable.just(
                PropertySource.of(secrets)
        );

        if (scheduler != null) {
            propertySourceFlowable = propertySourceFlowable.subscribeOn(scheduler);
        }
        propertySources.add(propertySourceFlowable);
        return Flowable.merge(propertySources);
    }

    private ListSecretsRequest buildRequest(String vaultId, String compartmentId, @Nullable String page) {
        ListSecretsRequest.Builder request = ListSecretsRequest.builder()
                .vaultId(vaultId)
                .compartmentId(compartmentId)
                .lifecycleState(SecretSummary.LifecycleState.Active);
        if (page != null) {
            request.page(page);
        }
        return request.build();
    }

    private String getSecretValue(String secretOcid) {
        GetSecretBundleRequest getSecretBundleRequest = GetSecretBundleRequest
                .builder()
                .secretId(secretOcid)
                .stage(GetSecretBundleRequest.Stage.Current)
                .build();

        GetSecretBundleResponse getSecretBundleResponse = secretsClient.
                getSecretBundle(getSecretBundleRequest);

        Base64SecretBundleContentDetails base64SecretBundleContentDetails =
                (Base64SecretBundleContentDetails) getSecretBundleResponse.
                        getSecretBundle().getSecretBundleContent();

        byte[] secretValueDecoded = Base64.decodeBase64(base64SecretBundleContentDetails.getContent());
        return new String(secretValueDecoded);
    }

    @Override
    public String getDescription() {
        return "Retrieves secrets from Oracle Cloud vaults";
    }
}
