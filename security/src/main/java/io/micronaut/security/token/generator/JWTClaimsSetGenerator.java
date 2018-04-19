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

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.security.authentication.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JWTClaimsSetGenerator implements ClaimsGenerator<JWTClaimsSet> {
    private static final int MILLISECONDS_IN_A_SECOND = 1000;
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTokenGenerator.class);

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param expiration expiration time in seconds
     * @return
     */
    @Override
    public Map<String, Object> generateClaims(UserDetails userDetails, Integer expiration) {
        return generateClaimsSet(userDetails, expiration).getClaims();
    }

    private JWTClaimsSet generateClaimsSet(UserDetails userDetails, Integer expiration) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.subject(userDetails.getUsername());

        Date now = new Date();
        builder.issueTime(now);

        if (expiration != null) {
            LOG.debug("Setting expiration to {}", expiration.toString());
            Date expirationTime = new Date(now.getTime() + (expiration * MILLISECONDS_IN_A_SECOND));
            builder.expirationTime(expirationTime);
        }

        builder.claim("roles", userDetails.getRoles());

        LOG.debug("Generated claim set: {}", builder.build().toJSONObject().toString());

        return builder.build();
    }

    /**
     *
     * @param claims The claims to be included in the JWT
     * @return Instance of {@link JWTClaimsSet}
     */
    @Override
    public JWTClaimsSet generateClaimsSet(Map<String, ?> claims) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        for (String k : claims.keySet()) {
            builder.claim(k, claims.get(k));
        }
        return builder.build();
    }
}
