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
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

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
     * @param userFetcher        Fetches users from persistence
     * @param passwordEncoder    Collaborator which checks if a raw password matches an encoded password
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
     *
     * @param authenticationRequest The authentication request data
     * @return An AuthenticationResponse object which encapsulates the authentication result.
     */
    @Override
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        final String username = authenticationRequest.getIdentity().toString();

        Flowable<UserState> userState = Flowable.fromPublisher(userFetcher.findByUsername(username));

        // TODO: handle exception?
        return userState.switchMap(user -> {
            if (!user.isEnabled()) {
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.USER_DISABLED));
            }
            if (user.isAccountExpired()) {
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.ACCOUNT_EXPIRED));
            }
            if (user.isAccountLocked()) {
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.ACCOUNT_LOCKED));
            }
            if (user.isPasswordExpired()) {
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.PASSWORD_EXPIRED));
            }
            if (!passwordEncoder.matches(authenticationRequest.getSecret().toString(), user.getPassword())) {
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH));
            }
            return Flowable
                .fromPublisher(authoritiesFetcher.findAuthoritiesByUsername(username))
                .map(authorities -> new UserDetails(username, authorities));
        }).switchIfEmpty(Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND)));

    }
}
