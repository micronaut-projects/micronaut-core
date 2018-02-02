/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import io.reactivex.Flowable;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.eureka.EurekaConfiguration;
import org.particleframework.discovery.eureka.EurekaServiceInstance;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.client.Client;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.jackson.annotation.JacksonFeatures;
import org.particleframework.validation.Validated;
import org.reactivestreams.Publisher;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static com.fasterxml.jackson.databind.DeserializationFeature.UNWRAP_ROOT_VALUE;
import static com.fasterxml.jackson.databind.SerializationFeature.WRAP_ROOT_VALUE;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED;

/**
 * Compile time implementation of {@link EurekaClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Client(id = EurekaClient.SERVICE_ID, path = "/eureka", configuration = EurekaConfiguration.class)
@JacksonFeatures(
    enabledSerializationFeatures = WRAP_ROOT_VALUE,
    disabledSerializationFeatures = WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,
    enabledDeserializationFeatures = {UNWRAP_ROOT_VALUE, ACCEPT_SINGLE_VALUE_AS_ARRAY}
)
@Validated
abstract class AbstractEurekaClient implements EurekaClient {

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        Flowable<List<ServiceInstance>> flowable = Flowable.fromPublisher(getApplicationInfo(serviceId)).map(applicationInfo -> {
            List<InstanceInfo> instances = applicationInfo.getInstances();
            return instances.stream()
                    .map(EurekaServiceInstance::new)
                    .collect(Collectors.toList());
        });

        return flowable.onErrorReturn(throwable -> {
            // Translate 404 into empty list
            if(throwable instanceof HttpClientResponseException) {
                HttpClientResponseException hcre = (HttpClientResponseException) throwable;
                if(hcre.getStatus() == HttpStatus.NOT_FOUND) {
                    return Collections.emptyList();
                }
            }
            if(throwable instanceof Exception) {
                throw (Exception)throwable;
            }
            else {
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

    @SuppressWarnings("WeakerAccess")
    @Get("/apps")
    public abstract Publisher<ApplicationInfos> getApplicationInfosInternal();

    @SuppressWarnings("WeakerAccess")
    @Get("/vips/{vipAddress}")
    public abstract Publisher<ApplicationInfos> getApplicationVipsInternal(String vipAddress);

    @JsonRootName("applications")
    static class ApplicationInfos {
        private List<ApplicationInfo> applications;

        @JsonCreator
        public ApplicationInfos(@JsonProperty("application") List<ApplicationInfo> applications) {
            this.applications = applications;
        }

        @JsonProperty("application")
        public List<ApplicationInfo> getApplications() {
            return applications;
        }
    }
}
