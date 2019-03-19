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

package io.micronaut.security.token.jwt.validator;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import io.micronaut.security.authentication.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.text.ParseException;
import java.util.Optional;

/**
 * Extracts the JWT claims and uses the {@link AuthenticationJWTClaimsSetAdapter} to construction an {@link Authentication} object.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class DefaultAuthenticationWithJwtGenerator implements AuthenticationWithJwtGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAuthenticationWithJwtGenerator.class);

    @Override
    public Optional<Authentication> createAuthentication(String token) {
        try {
            return Optional.of(createAuthentication(JWTParser.parse(token)));
        } catch (ParseException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("parse exception generating a JWT object and claims from token {}", token);
            }
        }
        return Optional.empty();
    }

    /**
     *
     * @param jwt A JSON Web Token
     * @return An Authentication object
     * @throws ParseException If the payload of the JWT doesn't represent a valid JSON object and a JWT claims set.
     */
    public Authentication createAuthentication(JWT jwt) throws ParseException {
        final JWTClaimsSet claimSet = jwt.getJWTClaimsSet();
        return new AuthenticationJWTClaimsSetAdapter(claimSet);
    }
}
