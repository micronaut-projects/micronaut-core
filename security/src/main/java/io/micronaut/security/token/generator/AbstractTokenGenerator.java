/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.security.token.generator;

import com.nimbusds.jwt.JWT;
import io.micronaut.security.authentication.UserDetails;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
abstract class AbstractTokenGenerator implements TokenGenerator {

    protected final TokenConfiguration tokenConfiguration;

    protected final JWTClaimsSetConverter jwtClaimsSetConverter;

    protected final ClaimsGenerator claimsGenerator;

    public AbstractTokenGenerator(TokenConfiguration tokenConfiguration,
                                  ClaimsGenerator claimsGenerator,
                                  JWTClaimsSetConverter jwtClaimsSetConverter) {
        this.tokenConfiguration = tokenConfiguration;
        this.claimsGenerator = claimsGenerator;
        this.jwtClaimsSetConverter = jwtClaimsSetConverter;
    }

    protected abstract Optional<JWT> generate(Map<String, Object> claims);

    @Override
    public Optional<String> generateToken(UserDetails userDetails, Integer expiration) {
        Map<String, Object> claims = claimsGenerator.generateClaims(userDetails, expiration);
        return generateToken(claims);
    }

    @Override
    public Optional<String> generateToken(Map<String, Object> claims) {
        Optional<JWT> jwt = generate(claims);
        return jwt.map(JWT::serialize);
    }
}
