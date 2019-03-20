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
public class DefaultJwtAuthenticationFactory implements JwtAuthenticationFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultJwtAuthenticationFactory.class);

    @Override
    public Optional<Authentication> createAuthentication(JWT token) {
        try {
            final JWTClaimsSet claimSet = token.getJWTClaimsSet();
            return Optional.of(new AuthenticationJWTClaimsSetAdapter(claimSet));
        } catch (ParseException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("ParseException creating authentication", e.getMessage());
            }
        }
        return Optional.empty();
    }
}
