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
import com.oracle.bmc.vault.VaultsClient;
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
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
     */
    public OracleCloudVaultConfigurationClient(OracleCloudVaultClientConfiguration oracleCloudVaultClientConfiguration,
                                    ApplicationConfiguration applicationConfiguration,
                                    @Named(TaskExecutors.IO) @Nullable ExecutorService executorService) throws Exception {
        this.oracleCloudVaultClientConfiguration = oracleCloudVaultClientConfiguration;
        this.applicationConfiguration = applicationConfiguration;
        this.executorService = executorService;

        BasicAuthenticationDetailsProvider provider = null;
        if( oracleCloudVaultClientConfiguration.isUseInstancePrincipal() ) {
            provider = InstancePrincipalsAuthenticationDetailsProvider.builder().build();
        } else {
            try {
                provider = new ConfigFileAuthenticationDetailsProvider(oracleCloudVaultClientConfiguration.getPathToConfig(), oracleCloudVaultClientConfiguration.getProfile());
            }
            catch (IOException e) {
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

        for (OracleCloudVaultClientConfiguration.OracleCloudVault vault : oracleCloudVaultClientConfiguration.getVaults()) {
            LOG.info("Working with OCID: " + vault.getOcid());
            ListSecretsRequest listSecretsRequest = ListSecretsRequest.builder()
                    .vaultId(vault.getOcid())
                    .compartmentId(vault.getCompartmentOcid())
                    .build();
            ListSecretsResponse listSecretsResponse = vaultsClient.listSecrets(listSecretsRequest);
            listSecretsResponse.getItems().forEach( (summary) -> {
                System.out.println(summary.getSecretName());
            });
        }

        /*
        buildVaultKeys(applicationName, activeNames).entrySet().forEach(entry -> {
            Flowable<PropertySource> propertySourceFlowable = Flowable.fromPublisher(
                    configHttpClient.readConfigurationValues(token, engine, entry.getValue()))
                    .filter(data -> !data.getSecrets().isEmpty())
                    .map(data -> PropertySource.of(entry.getValue(), data.getSecrets(), entry.getKey()))
                    .onErrorResumeNext(throwable -> {
                        //TODO: Discover why the below hack is necessary
                        Throwable t = (Throwable) throwable;
                        if (t instanceof HttpClientResponseException) {
                            if (((HttpClientResponseException) t).getStatus() == HttpStatus.NOT_FOUND) {
                                if (vaultClientConfiguration.isFailFast()) {
                                    return Flowable.error(new ConfigurationException(
                                            "Could not locate PropertySource and the fail fast property is set", t));
                                }
                            }
                            return Flowable.empty();
                        }
                        return Flowable.error(new ConfigurationException("Error reading distributed configuration from Vault: " + t.getMessage(), t));
                    });
            if (scheduler != null) {
                propertySourceFlowable = propertySourceFlowable.subscribeOn(scheduler);
            }
            propertySources.add(propertySourceFlowable);
        });
        */
        return Flowable.merge(propertySources);
    }

    @Override
    public String getDescription() {
        return "Retrieves secrets from Oracle Cloud vaults";
    }
}
