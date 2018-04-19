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

package io.micronaut.security.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class Authenticator {
    private static final Logger LOG = LoggerFactory.getLogger(Authenticator.class);

    private Collection<AuthenticationProvider> authenticationProviders;

    /**
     *
     * @param authenticationProviders a List of availabble authentication providers
     */
    public Authenticator(Collection<AuthenticationProvider> authenticationProviders) {
        this.authenticationProviders = authenticationProviders;
    }

    /**
     *
     * @param credentials instance of {@link UsernamePasswordCredentials}
     * @return Empty optional if authentication failed. If any {@link AuthenticationProvider} authenticates, that {@link AuthenticationResponse} is sent.
     */
    public Optional<AuthenticationResponse> authenticate(UsernamePasswordCredentials credentials) {
        if ( authenticationProviders != null ) {
            for ( AuthenticationProvider authenticationProvider : authenticationProviders ) {
                try {
                    AuthenticationResponse rsp = authenticationProvider.authenticate(credentials);
                    if ( rsp.isAuthenticated() ) {
                        return Optional.of(rsp);
                    }
                } catch (Exception e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Authentication provider threw exception", e);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
