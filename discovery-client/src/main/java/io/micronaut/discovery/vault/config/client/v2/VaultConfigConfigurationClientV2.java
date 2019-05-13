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

package io.micronaut.discovery.vault.config.client.v2;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.vault.VaultClientConfiguration;
import io.micronaut.discovery.vault.condition.RequiresVaultClientConfig;
import io.micronaut.discovery.vault.config.client.AbstractVaultConfigConfigurationClient;
import io.micronaut.discovery.vault.config.client.v2.condition.RequiresVaultClientConfigV2;
import io.micronaut.discovery.vault.config.client.v2.response.VaultResponseData;
import io.micronaut.discovery.vault.config.client.v2.response.VaultResponseV2;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *  A {@link ConfigurationClient} for Vault Configuration.
 *
 *  @author thiagolocatelli
 *  @author graemerocher
 *  @since 1.1.1
 */
@Singleton
@RequiresVaultClientConfig
@RequiresVaultClientConfigV2
@Requires(property = ConfigurationClient.ENABLED, value = "true", defaultValue = "false")
@BootstrapContextCompatible
public class VaultConfigConfigurationClientV2 extends AbstractVaultConfigConfigurationClient {

    private static final Logger LOG = LoggerFactory.getLogger(VaultConfigConfigurationClientV2.class);

    private final VaultConfigHttpClientV2 vaultConfigClientV2;
    private final String vaultUri;

    /**
     * Default Constructor.
     *
     * @param vaultConfigClientV2       Vault Config Http Client
     * @param vaultClientConfiguration  Vault Client Configuration
     * @param applicationConfiguration  The application configuration
     * @param environment               The environment
     * @param vaultUri                  Vault endpoint uri
     * @param executorService           Executor Service
     */
    public VaultConfigConfigurationClientV2(final VaultConfigHttpClientV2 vaultConfigClientV2,
                                            final VaultClientConfiguration vaultClientConfiguration,
                                            final ApplicationConfiguration applicationConfiguration,
                                            final Environment environment,
                                            @Value(VaultClientConfiguration.VAULT_CLIENT_CONFIG_ENDPOINT) final String vaultUri,
                                            @Named(TaskExecutors.IO) @Nullable final ExecutorService executorService) {

        super(vaultClientConfiguration, applicationConfiguration, environment, executorService);
        this.vaultConfigClientV2 = vaultConfigClientV2;
        this.vaultUri = vaultUri;
    }

    @Override
    protected Flowable<PropertySource> getProperySources(Set<String> activeNames) {

        final AtomicInteger sourceCount = new AtomicInteger(0);
        final List<String> activeNamesList = new ArrayList<>(activeNames);

        Function<Throwable, Publisher<? extends VaultResponseV2>> errorHandler = getErrorHandler(sourceCount);
        List<PairVaultResponse> configurationValuesList = retrieveVaultProperties(activeNames);

        return Flowable.fromIterable(configurationValuesList).flatMap(pairVaultResponse -> {
            return pairVaultResponse.getRight().onErrorResumeNext(errorHandler)
                    .flatMap(vaultResponse -> Flowable.create(emitter -> {
                VaultResponseData vaultResponseData = vaultResponse.getData();

                synchronized (sourceCount) {
                    sourceCount.getAndIncrement();
                }

                if (!CollectionUtils.isEmpty(vaultResponseData.getData())) {
                    String vaultSourceName = getVaultSourceName(activeNames, pairVaultResponse.getLeft());

                    if (LOG.isInfoEnabled()) {
                        LOG.info("Obtained property source from Vault, source={}", vaultSourceName);
                    }
                    emitter.onNext(PropertySource.of(vaultSourceName,
                            vaultResponseData.getData(), Integer.MAX_VALUE - activeNamesList.indexOf(pairVaultResponse.getLeft())));
                }

                //if all items have been processed, emit onComplete
                if (sourceCount.get() == configurationValuesList.size()) {
                    emitter.onComplete();
                }
            }, BackpressureStrategy.ERROR));
        });
    }

    /**
     * @param activeNames active environment names
     * @return list of responses from vault
     */
    private List<PairVaultResponse> retrieveVaultProperties(Set<String> activeNames) {
        final String applicationName = getApplicationConfiguration().getName().get();

        return activeNames.stream().map(activeName -> new PairVaultResponse(activeName,
                activeName.equals(applicationName) ?
                        Flowable.fromPublisher(
                                vaultConfigClientV2.readConfigurationValues(
                                        getVaultClientConfiguration().getBackend(), applicationName)) :
                        Flowable.fromPublisher(
                                vaultConfigClientV2.readConfigurationValues(
                                        getVaultClientConfiguration().getBackend(), applicationName, activeName))
        )).collect(Collectors.toList());
    }

    /**
     * @param sourceCount total of property sources from vault
     * @return the error handler to handle vault failed requests
     */
    private Function<Throwable, Publisher<? extends VaultResponseV2>>  getErrorHandler(AtomicInteger sourceCount) {
        return throwable -> {

            synchronized (sourceCount) {
                sourceCount.getAndIncrement();
            }

            if (throwable instanceof HttpClientResponseException) {
                HttpClientResponseException httpClientResponseException = (HttpClientResponseException) throwable;
                if (httpClientResponseException.getStatus() == HttpStatus.NOT_FOUND) {
                    if (getVaultClientConfiguration().isFailFast()) {
                        return Flowable.error(new IllegalStateException(
                                "Could not locate PropertySource and the fail fast property is set",
                                throwable));
                    }
                    LOG.warn("Could not locate PropertySource: ", throwable);
                    return Flowable.empty();
                }
            }
            return Flowable.error(throwable);
        };
    }

    @Override
    public String getDescription() {
        return VaultConfigHttpClientV2.CLIENT_DESCRIPTION;
    }

    /**
     * Wrapper for Vault Response.
     */
    private class PairVaultResponse extends Pair<String, Flowable<VaultResponseV2>> {
        public PairVaultResponse(String left, Flowable<VaultResponseV2> right) {
            super(left, right);
        }
    }

}
