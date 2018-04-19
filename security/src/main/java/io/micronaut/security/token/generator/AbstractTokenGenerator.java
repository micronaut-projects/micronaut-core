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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import io.micronaut.security.authentication.UserDetails;
import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
abstract class AbstractTokenGenerator implements TokenGenerator {

    protected final TokenConfiguration tokenConfiguration;
    protected final JWTClaimsSetGenerator claimsGenerator;

    /**
     *
     * @param tokenConfiguration Instance of {@link TokenConfiguration}
     * @param claimsGenerator Instance of {@link JWTClaimsSetGenerator}
     */
    AbstractTokenGenerator(TokenConfiguration tokenConfiguration,
                                  JWTClaimsSetGenerator claimsGenerator) {
        this.tokenConfiguration = tokenConfiguration;
        this.claimsGenerator = claimsGenerator;
    }

    /**
     *
     * @param claims Claims to be included in the JWT
     * @return a JWT token
     * @throws JOSEException a {@link JOSEException}
     */
    protected abstract JWT generate(Map<String, Object> claims) throws JOSEException;

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param expiration The amount of time in milliseconds until the token expires
     * @return
     * @throws JOSEException
     */
    @Override
    public String generateToken(UserDetails userDetails, Integer expiration) throws JOSEException {
        Map<String, Object> claims = claimsGenerator.generateClaims(userDetails, expiration);
        return generateToken(claims);
    }

    /**
     *
     * @param claims Claims to be included in the JWT token to be generated
     * @return
     * @throws JOSEException
     */
    @Override
    public String generateToken(Map<String, Object> claims) throws JOSEException {
        JWT jwt = generate(claims);
        return jwt.serialize();
    }
}
