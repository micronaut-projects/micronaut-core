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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.vault.config.client.v1.response.VaultResponseV1;
import io.micronaut.discovery.vault.config.client.v2.response.VaultResponseData;
import io.micronaut.discovery.vault.config.client.v2.response.VaultResponseV2;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Controller("/")
@Requires(property = MockingVaultServerController.ENABLED)
public class MockingVaultServerController {

    public static final String ENABLED = "enable.mock.vault-config";
    final static Logger LOGGER = LoggerFactory.getLogger(MockingVaultServerController.class);

    @Get("/v1/{backend}/{applicationName}")
    public Publisher<VaultResponseV1> readConfigurationValues(@Nonnull String backend,
                                                              @Nonnull String applicationName) {

        return getVaultResponseV1(backend, applicationName, applicationName);
    }

    @Get("/v1/{backend}/{applicationName}/{profile}")
    public Publisher<VaultResponseV1> readConfigurationValues(@Nonnull String backend,
                                                              @Nonnull String applicationName,
                                                              @Nonnull String profile) {

        return getVaultResponseV1(backend, applicationName, profile);
    }

    @Get("/v1/{backend}/data/{applicationName}")
    public Publisher<VaultResponseV2> readConfigurationValuesV2(@Nonnull String backend,
                                                              @Nonnull String applicationName) {

        return getVaultResponseV2(backend, applicationName, applicationName);
    }

    @Get("/v1/{backend}/data/{applicationName}/{profile}")
    public Publisher<VaultResponseV2> readConfigurationValuesV2(@Nonnull String backend,
                                                              @Nonnull String applicationName,
                                                              @Nonnull String profile) {

        return getVaultResponseV2(backend, applicationName, profile);
    }


    private Publisher<VaultResponseV1> getVaultResponseV1(@Nonnull String backend,
                                                        @Nonnull String applicationName,
                                                        @Nonnull String profile) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("vault-backend-key-one", applicationName + (StringUtils.isNotEmpty(profile) ? "-" + profile : ""));
        properties.put("vault-backend-name", backend + (StringUtils.isNotEmpty(profile) ? "-" + profile : ""));
        properties.put("vault-backend-kv-version", "v1" + (StringUtils.isNotEmpty(profile) ? "-" + profile : ""));

        VaultResponseV1 response = new VaultResponseV1(properties,
                null,
                null,
                null,
                Collections.emptyMap(),
                false,
                Collections.emptyList());

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            LOGGER.info(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Flowable.just(response);
    }

    private Publisher<VaultResponseV2> getVaultResponseV2(@Nonnull String backend,
                                                        @Nonnull String applicationName,
                                                        @Nonnull String profile) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("vault-backend-key-one", applicationName + (StringUtils.isNotEmpty(profile) ? "-" + profile : ""));
        properties.put("vault-backend-name", backend + (StringUtils.isNotEmpty(profile) ? "-" + profile : ""));
        properties.put("vault-backend-kv-version", "v1" + (StringUtils.isNotEmpty(profile) ? "-" + profile : ""));

        VaultResponseData vaultResponseData = new VaultResponseData(properties, Collections.emptyMap());

        VaultResponseV2 response = new VaultResponseV2(vaultResponseData,
                null,
                null,
                null,
                Collections.emptyMap(),
                false,
                Collections.emptyList());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            LOGGER.info(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return Flowable.just(response);
    }

}