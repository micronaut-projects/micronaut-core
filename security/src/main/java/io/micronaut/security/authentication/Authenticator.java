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

import io.micronaut.context.BeanContext;

import javax.annotation.PostConstruct;
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

    protected final BeanContext beanContext;

    private Collection<AuthenticationProvider> authenticationProviders;

    public Authenticator(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @PostConstruct
    protected void initialize() {
        authenticationProviders = beanContext.getBeansOfType(AuthenticationProvider.class);
    }

    /**
     *
     * @param credentials instance of {@link UsernamePasswordCredentials}
     * @return Empty optional if authentication failed. If any {@link AuthenticationProvider} authenticates, that {@link AuthenticationResponse} is sent.
     */
    public Optional<AuthenticationResponse> authenticate(UsernamePasswordCredentials credentials) {
        if ( authenticationProviders != null ) {
            for ( AuthenticationProvider authenticationProvider : authenticationProviders ) {
                AuthenticationResponse rsp = authenticationProvider.authenticate(credentials);
                if ( rsp.isAuthenticated() ) {
                    return Optional.of(rsp);
                }
            }
        }
        return Optional.empty();
    }
}
