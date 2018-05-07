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

package io.micronaut.security.token.jwt.generator.claims;

import io.micronaut.security.authentication.UserDetails;

import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface ClaimsGenerator {

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param expiration JWT token expiration time in seconds
     * @return The Claims
     */
    Map<String, Object> generateClaims(UserDetails userDetails, Integer expiration);

    /**
     * Generate a claims set based on claims.
     *
     * @param oldClaims The old claims to use as a base in the new token generation.
     * @param expiration JWT token expiration time in seconds
     * @return The Claims
     */
    Map<String, Object> generateClaimsSet(Map<String, ?> oldClaims, Integer expiration);
}
