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
import io.micronaut.discovery.vault.config.client.v1.response.VaultResponseV1;
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

/**
 *  Mocking Controller for Vault KV version 1
 *
 *  @author thiagolocatelli
 */
@Controller
@Requires(property = MockingVaultServerV1Controller.ENABLED)
public class MockingVaultServerV1Controller {

    public static final String ENABLED = "enable.mock.vault-config-v1";
    public final static Logger LOGGER = LoggerFactory.getLogger(MockingVaultServerV1Controller.class);

    @Get("/v1/{backend}/{vaultKey:.*}")
    public Publisher<VaultResponseV1> readConfigurationValuesV1(@Nonnull String backend,
                                                              @Nonnull String vaultKey) {
        return getVaultResponseV1(backend, vaultKey);
    }

    private Publisher<VaultResponseV1> getVaultResponseV1(@Nonnull String backend,
                                                        @Nonnull String vaultKey) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("vault-backend-key-one", vaultKey);
        properties.put("vault-backend-name", backend + "-" + vaultKey);
        properties.put("vault-backend-kv-version", "v1" + "-" +vaultKey);

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

}