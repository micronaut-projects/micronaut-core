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

package io.micronaut.security.token.generator;

import io.micronaut.security.authentication.UserDetails;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Responsible for generating token strings.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface TokenGenerator {

    /**
     * @param userDetails Authenticated user's representation.
     * @param expiration The amount of time in milliseconds until the token expires
     * @return An optional JWT string
     */
    Optional<String> generateToken(UserDetails userDetails, @Nullable Integer expiration);

    /**
     * @param claims Claims to be included in the JWT token to be generated
     * @return a JSON Web Token ( JWT )
     */
    Optional<String> generateToken(Map<String, Object> claims);
}
