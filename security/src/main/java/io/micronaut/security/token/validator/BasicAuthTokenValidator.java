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

package io.micronaut.security.token.validator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.Authenticator;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.reader.BasicAuthTokenReaderConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Basic Auth Token Validator.
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = BasicAuthTokenReaderConfigurationProperties.PREFIX + ".enabled")
@Singleton
public class BasicAuthTokenValidator implements TokenValidator {

    /**
     * The order of the TokenValidator.
     */
    public static final Integer ORDER = 0;

    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthTokenValidator.class);

    protected final Authenticator authenticator;

    /**
     *
     * @param authenticator The Authenticator
     */
    public BasicAuthTokenValidator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public Optional<Authentication> validateToken(String encodedToken) {

        Optional<UsernamePasswordCredentials> creds = credsFromEncodedToken(encodedToken);
        if (creds.isPresent()) {
            Optional<AuthenticationResponse> response = authenticator.authenticate(creds.get());
            if (response.map(AuthenticationResponse::isAuthenticated).orElse(false)) {
                UserDetails userDetails = (UserDetails) response.get();
                return Optional.of(authenticationOfUserDetails(userDetails));
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not authenticate {}", creds.get().getUsername());
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private Authentication authenticationOfUserDetails(UserDetails userDetails) {
        return new Authentication() {
            @Override
            public String getId() {
                return userDetails.getUsername();
            }

            @Override
            public Map<String, Object> getAttributes() {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("roles", userDetails.getRoles());
                attributes.put("username", userDetails.getUsername());
                return attributes;
            }
        };
    }

    private Optional<UsernamePasswordCredentials> credsFromEncodedToken(String encodedToken) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedToken);
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error while trying to Base 64 decode: {}", encodedToken);
            }
            return Optional.empty();
        }

        String token;
        try {
            token = new String(decoded, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad format of the basic auth header");
            }
            return Optional.empty();
        }

        final int delim = token.indexOf(":");
        if (delim < 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad format of the basic auth header - Delimiter : not found");
            }
            return Optional.empty();
        }

        final String username = token.substring(0, delim);
        final String password = token.substring(delim + 1);
        return Optional.of(new UsernamePasswordCredentials(username, password));
    }
}
