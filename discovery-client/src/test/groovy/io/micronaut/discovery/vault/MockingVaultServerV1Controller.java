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

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.vault.config.v1.VaultResponseV1;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

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

    @Get("/v1/{backend}/{vaultKey:.*}")
    public Publisher<VaultResponseV1> readConfigurationValuesV1(@Nonnull String backend,
                                                                @Nonnull String vaultKey) {
        Map<String, Object> properties = new HashMap<>();

        if (vaultKey.equals("myapp/test")) {
            properties.put("v1-secret-1", 1);
        } else if (vaultKey.equals("application/test")) {
            properties.put("v1-secret-1", 2);
            properties.put("v1-secret-2", 1);
        } else if (vaultKey.equals("myapp")) {
            properties.put("v1-secret-1", 3);
            properties.put("v1-secret-2", 2);
            properties.put("v1-secret-3", 1);
        } else if (vaultKey.equals("application")) {
            properties.put("v1-secret-1", 4);
            properties.put("v1-secret-2", 3);
            properties.put("v1-secret-3", 2);
            properties.put("v1-secret-4", 1);
        }

        VaultResponseV1 response = new VaultResponseV1(properties,
                null,
                null,
                null,
                Collections.emptyMap(),
                false,
                Collections.emptyList());

        return Flowable.just(response);
    }

}