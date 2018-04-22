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

package io.micronaut.security.token.configuration;

/**
 * Represents configuration of the JWT token.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface TokenConfiguration {

    /**
     * The name in the claims object that stores the granted roles.
     *
     * @return The roles claim name, e.g. roles.
     */
    String getRolesClaimName();

    /**
     * How long until the access token expires in milliseconds.
     *
     * @return The time, or null if not set to expire
     */
    Integer getAccessTokenExpiration();

    /**
     * How long until the refresh token expires in milliseconds.
     *
     * @return The time, or null if not set to expire
     */
    Integer getRefreshTokenExpiration();
}
