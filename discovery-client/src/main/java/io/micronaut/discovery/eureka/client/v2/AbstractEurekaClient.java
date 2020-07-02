/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.eureka.client.v2;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static com.fasterxml.jackson.databind.DeserializationFeature.UNWRAP_ROOT_VALUE;
import static com.fasterxml.jackson.databind.SerializationFeature.WRAP_ROOT_VALUE;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.eureka.EurekaConfiguration;
import io.micronaut.discovery.eureka.EurekaServiceInstance;
import io.micronaut.discovery.eureka.condition.RequiresEureka;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.validation.Validated;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Compile time implementation of {@link EurekaClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Client(id = EurekaClient.SERVICE_ID, path = EurekaConfiguration.CONTEXT_PATH_PLACEHOLDER, configuration = EurekaConfiguration.class)
@JacksonFeatures(
    enabledSerializationFeatures = WRAP_ROOT_VALUE,
    disabledSerializationFeatures = WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,
    enabledDeserializationFeatures = {UNWRAP_ROOT_VALUE, ACCEPT_SINGLE_VALUE_AS_ARRAY}
)
@Validated
@RequiresEureka
abstract class AbstractEurekaClient implements EurekaClient {

    static final String EXPR_EUREKA_REGISTRATION_RETRY_DELAY = "${" + EurekaConfiguration.EurekaRegistrationConfiguration.PREFIX + ".retry-delay:3s}";
    static final String EXPR_EUREKA_REGISTRATION_RETRY_COUNT = "${" + EurekaConfiguration.EurekaRegistrationConfiguration.PREFIX + ".retry-count:10}";

    private final EurekaConfiguration.EurekaDiscoveryConfiguration discoveryConfiguration;

    /**
     * Default constructor.
     *
     * @param discoveryConfiguration The discovery configuration.
     */
    protected AbstractEurekaClient(EurekaConfiguration.EurekaDiscoveryConfiguration discoveryConfiguration) {
        this.discoveryConfiguration = discoveryConfiguration;
    }

    @Override
    public String getDescription() {
        return EurekaClient.SERVICE_ID;
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        serviceId = NameUtils.hyphenate(serviceId);
        Flowable<List<ServiceInstance>> flowable = Flowable.fromPublisher(getApplicationInfo(serviceId)).map(applicationInfo -> {
            List<InstanceInfo> instances = applicationInfo.getInstances();
            return instances.stream()
                .map(ii -> {
                    if (!discoveryConfiguration.isUseSecurePort()) {
                        ii.setSecurePort(-1);
                    }
                    return new EurekaServiceInstance(ii);
                })
                .collect(Collectors.toList());
        });

        return flowable.onErrorReturn(throwable -> {
            // Translate 404 into empty list
            if (throwable instanceof HttpClientResponseException) {
                HttpClientResponseException hcre = (HttpClientResponseException) throwable;
                if (hcre.getStatus() == HttpStatus.NOT_FOUND) {
                    return Collections.emptyList();
                }
            }
            if (throwable instanceof Exception) {
                throw (Exception) throwable;
            } else {
                throw new HttpClientException("Internal Client Error: " + throwable.getMessage(), throwable);
            }
        });
    }

    @Override
    public Publisher<List<ApplicationInfo>> getApplicationInfos() {
        return Publishers.map(getApplicationInfosInternal(), applicationInfos -> applicationInfos.applications);
    }

    @Override
    public Publisher<List<ApplicationInfo>> getApplicationVips(String vipAddress) {
        return Publishers.map(getApplicationVipsInternal(vipAddress), applicationInfos -> applicationInfos.applications);
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        return Publishers.map(getApplicationInfosInternal(), applicationInfos ->
            applicationInfos
                .applications
                .stream()
                .map(ApplicationInfo::getName)
                .collect(Collectors.toList())
        );
    }

    /**
     * @return A {@link Publisher} with applications info.
     */
    @SuppressWarnings("WeakerAccess")
    @Get("/apps")
    @Produces(single = true)
    public abstract Publisher<ApplicationInfos> getApplicationInfosInternal();

    /**
     * @param vipAddress The vip address
     * @return A {@link Publisher} with applications info
     */
    @SuppressWarnings("WeakerAccess")
    @Get("/vips/{vipAddress}")
    @Produces(single = true)
    public abstract Publisher<ApplicationInfos> getApplicationVipsInternal(String vipAddress);

    /**
     * Class for the applications info.
     */
    @JsonRootName("applications")
    static class ApplicationInfos {
        private List<ApplicationInfo> applications;

        /**
         * @param applications The list of applications info
         */
        @JsonCreator
        public ApplicationInfos(@JsonProperty("application") List<ApplicationInfo> applications) {
            this.applications = applications != null ? applications : Collections.emptyList();
        }

        /**
         * @return The applications info
         */
        @JsonProperty("application")
        public List<ApplicationInfo> getApplications() {
            return applications;
        }
    }
}
