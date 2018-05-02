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

package io.micronaut.security.authentication.providers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.*;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * AuthenticationProvider typically used with a persistence mechanism such as a DB.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(beans = {UserFetcher.class, PasswordEncoder.class, AuthoritiesFetcher.class})
@Singleton
public class DelegatingAuthenticationProvider implements AuthenticationProvider {

    protected final UserFetcher userFetcher;
    protected final PasswordEncoder passwordEncoder;
    protected final AuthoritiesFetcher authoritiesFetcher;

    /**
     *
     * @param userFetcher Fetches users from persistence
     * @param passwordEncoder Collaborator which checks if a raw password matches an encoded password
     * @param authoritiesFetcher Fetches authorities for a particular user
     */
    public DelegatingAuthenticationProvider(UserFetcher userFetcher,
                                            PasswordEncoder passwordEncoder,
                                            AuthoritiesFetcher authoritiesFetcher) {
        this.userFetcher = userFetcher;
        this.passwordEncoder = passwordEncoder;
        this.authoritiesFetcher = authoritiesFetcher;
    }

    /**
     * Attempts to authenticate a user.
     * @param authenticationRequest The authentication request data
     * @return An AuthenticationResponse object which encapsulates the authentication result.
     */
    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        final String username = authenticationRequest.getIdentity().toString();
        Optional<UserState> optionalUserState = userFetcher.findByUsername(username);

        if (!optionalUserState.isPresent()) {
            return new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND);
        }
        UserState user = optionalUserState.get();
        if (!user.isEnabled()) {
            return new AuthenticationFailed(AuthenticationFailureReason.USER_DISABLED);
        }
        if (user.isAccountExpired()) {
            return new AuthenticationFailed(AuthenticationFailureReason.ACCOUNT_EXPIRED);
        }
        if (user.isAccountLocked()) {
            return new AuthenticationFailed(AuthenticationFailureReason.ACCOUNT_LOCKED);
        }
        if (user.isPasswordExpired()) {
            return new AuthenticationFailed(AuthenticationFailureReason.PASSWORD_EXPIRED);
        }
        if (!passwordEncoder.matches(authenticationRequest.getSecret().toString(), user.getPassword())) {
            return new AuthenticationFailed(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }
        List<String> authorities = authoritiesFetcher.findAuthoritiesByUsername(username);
        return new UserDetails(username, authorities);
    }
}
