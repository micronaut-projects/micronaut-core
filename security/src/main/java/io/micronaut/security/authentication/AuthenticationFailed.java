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
import java.util.Optional;

/**
 * Signalises an authentication failure and stores the failure reason.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class AuthenticationFailed implements AuthenticationResponse {

    private final AuthenticationFailureReason authenticationFailure;
    private String message;

    /**
     * Necessary for JSON Serialization.
     */
    public AuthenticationFailed() {
        this(AuthenticationFailureReason.UNKNOWN);
    }

    /**
     * @param authenticationFailure AuthenticationFailure enum which represents the failure reason
     */
    public AuthenticationFailed(AuthenticationFailureReason authenticationFailure) {
        this.authenticationFailure = authenticationFailure;
        this.message = createMessage(authenticationFailure);
    }

    /**
     * Generates a Title Case string for give authentication Failure.
     * @param authenticationFailure the authentication failure
     * @return the Title Case String
     */
    protected String createMessage(AuthenticationFailureReason authenticationFailure) {
        StringBuilder sb = new StringBuilder(authenticationFailure.name().toLowerCase());
        for (int i = 0; i < sb.length(); i++) {
            int end = i + 1;
            if (i == 0) {
                sb.replace(i, end, String.valueOf(Character.toUpperCase(sb.charAt(i))));
            }
            if (sb.charAt(i) == '_') {
                sb.replace(i, end, " ");
                sb.replace(end, end + 1, String.valueOf(Character.toUpperCase(sb.charAt(i + 1))));
            }
        }
        return sb.toString();
    }

    /**
     * message getter.
     * @return Failure message
     */
    @Override
    public Optional<String> getMessage() {
        return Optional.of(message);
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
