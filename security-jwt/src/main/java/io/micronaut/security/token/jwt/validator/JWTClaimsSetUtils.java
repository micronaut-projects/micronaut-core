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

package io.micronaut.security.token.jwt.validator;

import com.nimbusds.jwt.JWTClaimsSet;

import java.util.Map;

/**
 * Utils class to instantiate a JWClaimsSet give a map of claims.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class JWTClaimsSetUtils {

    /**
     *
     * @param claims Map of Claims
     * @return A JWTClaimsSet
     */
    public static JWTClaimsSet jwtClaimsSetFromClaims(Map<String, Object> claims) {
        JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
        for (String k : claims.keySet()) {
            claimsSetBuilder.claim(k, claims.get(k));
        }
        return claimsSetBuilder.build();
    }
}
