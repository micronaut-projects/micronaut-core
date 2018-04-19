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

import java.util.Objects;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class AuthenticationFailed implements AuthenticationResponse {

    private AuthenticationFailure authenticationFailure = AuthenticationFailure.CREDENTIALS_DO_NOT_MATCH;

    public AuthenticationFailed() {}

    public AuthenticationFailed(AuthenticationFailure authenticationFailure) {
        this.authenticationFailure = authenticationFailure;
    }

    public AuthenticationFailure getAuthenticationFailure() {
        return authenticationFailure;
    }

    public void setAuthenticationFailure(AuthenticationFailure authenticationFailure) {
        this.authenticationFailure = authenticationFailure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AuthenticationFailed that = (AuthenticationFailed) o;
        return authenticationFailure == that.authenticationFailure;
    }

    @Override
    public int hashCode() {
        return Objects.hash(authenticationFailure);
    }
}
