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

import com.nimbusds.jwt.JWTClaimsSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Adapts from {@link JWTClaimsSet} to {@link JwtClaims}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class JwtClaimsSetAdapter implements JwtClaims {

    private final JWTClaimsSet jwtClaimsSet;

    /**
     * Constructor.
     * @param jwtClaimsSet a JWT Claims set
     */
    public JwtClaimsSetAdapter(JWTClaimsSet jwtClaimsSet) {
        this.jwtClaimsSet = jwtClaimsSet;
    }

    @Nullable
    @Override
    public Object get(String claimName) {
        return jwtClaimsSet.getClaim(claimName);
    }

    @Nonnull
    @Override
    public Set<String> names() {
        return jwtClaimsSet.getClaims().keySet();
    }

    @Override
    public boolean contains(String claimName) {
        return jwtClaimsSet.getClaims().containsKey(claimName);
    }
}
