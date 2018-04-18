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
import io.micronaut.security.authentication.AuthenticationFailure;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.authentication.UsernamePasswordCredentials;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Requires(beans = {UserFetcher.class, PasswordEncoder.class, AuthoritiesFetcher.class})
@Singleton
public class PersistenceAuthenticationProvider implements AuthenticationProvider {

    protected final UserFetcher userFetcher;
    protected final PasswordEncoder passwordEncoder;
    protected final AuthoritiesFetcher authoritiesFetcher;

    public PersistenceAuthenticationProvider(UserFetcher userFetcher,
                                             PasswordEncoder passwordEncoder,
                                             AuthoritiesFetcher authoritiesFetcher) {
        this.userFetcher = userFetcher;
        this.passwordEncoder = passwordEncoder;
        this.authoritiesFetcher = authoritiesFetcher;
    }

    @Override
    public AuthenticationResponse authenticate(UsernamePasswordCredentials creds) {
        Optional<UserState> optionalUserState = userFetcher.findByUsername(creds.getUsername());

        if ( !optionalUserState.isPresent()) {
            return new AuthenticationFailed(AuthenticationFailure.USER_NOT_FOUND);
        }
        UserState user = optionalUserState.get();
        if (!user.isEnabled() ) {
            return new AuthenticationFailed(AuthenticationFailure.USER_DISABLED);
        }
        if ( user.isAccountExpired() ) {
            return new AuthenticationFailed(AuthenticationFailure.ACCOUNT_EXPIRED);
        }
        if ( user.isAccountLocked() ) {
            return new AuthenticationFailed(AuthenticationFailure.ACCOUNT_LOCKED);
        }
        if ( user.isPasswordExpired() ) {
            return new AuthenticationFailed(AuthenticationFailure.PASSWORD_EXPIRED);
        }
        if ( !passwordEncoder.matches(creds.getPassword(), user.getPassword()) ) {
            return new AuthenticationFailed(AuthenticationFailure.CREDENTIALS_DO_NOT_MATCH);
        }
        List<String> authorities = authoritiesFetcher.findAuthoritiesByUsername(user.getUsername());
        return new UserDetails(user.getUsername(), authorities);
    }
}
