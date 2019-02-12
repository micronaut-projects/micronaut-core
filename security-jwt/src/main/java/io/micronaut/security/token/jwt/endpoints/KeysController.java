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
package io.micronaut.security.token.jwt.endpoints;

import com.nimbusds.jose.jwk.JWKSet;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.reactivex.Flowable;
import io.reactivex.Single;
import net.minidev.json.JSONObject;

import java.util.Collection;

/**
 * Endpoint which exposes a JSON Web Key Set built with the JWK provided by {@link io.micronaut.security.token.jwt.endpoints.JwkProvider} beans.
 *
 * @since 1.1.0
 * @author Sergio del Amo
 */
@Requires(property = KeysControllerConfigurationProperties.PREFIX + ".enabled", value = StringUtils.TRUE)
@Controller("${" + KeysControllerConfigurationProperties.PREFIX + ".path:/keys}")
@Secured(SecurityRule.IS_ANONYMOUS)
public class KeysController {

    private final Collection<JwkProvider> jwkProviders;

    /**
     * Instantiates a {@link io.micronaut.security.token.jwt.endpoints.KeysController}.
     * @param jwkProviders a collection of JSON Web Key providers.
     */
    public KeysController(Collection<JwkProvider> jwkProviders) {
        this.jwkProviders = jwkProviders;
    }

    /**
     *
     * @return a JSON Web Key Set (JWKS) payload.
     */
    @Get
    public Single<JSONObject> keys() {
        return Flowable.fromIterable(jwkProviders)
                .map(JwkProvider::retrieveJsonWebKey)
                .toList()
                .map(JWKSet::new)
                .map(JWKSet::toJSONObject);
    }
}
