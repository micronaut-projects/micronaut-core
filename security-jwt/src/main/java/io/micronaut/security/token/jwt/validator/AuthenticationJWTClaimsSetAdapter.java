/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.security.authentication.Authentication;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter from {@link JWTClaimsSet} to {@link Authentication}.
 * @author Sergio del Amo
 * @since 1.0
 */
public class AuthenticationJWTClaimsSetAdapter implements Authentication {
    @Nullable
    private JWTClaimsSet claimSet;

    /**
     *
     * @param claimSet JSON Web Token (JWT) claims set.
     */
    public AuthenticationJWTClaimsSetAdapter(@Nullable JWTClaimsSet claimSet) {
        this.claimSet = claimSet;
    }

    @Override
    @NonNull
    public Map<String, Object> getAttributes() {
        return claimSet == null ? new HashMap<>() : claimSet.getClaims();
    }

    @Override
    @Nullable
    public String getName() {
        return claimSet == null ? null : claimSet.getSubject();
    }
}
