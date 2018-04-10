/*
 * Copyright 2017 original authors
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
import io.micronaut.security.authentication.AuthenticationSuccess;
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
public class JwtClaimsGenerator implements ClaimsGenerator {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenGenerator.class);

    /**
     *
     * @param authenticationSuccess
     * @param expiration expiration time in seconds
     * @return
     */
    @Override
    public Map<String, Object> generateClaims(AuthenticationSuccess authenticationSuccess, Integer expiration) {
        JWTClaimsSet.Builder builder = generateClaimsBuilder(authenticationSuccess, expiration);
        JWTClaimsSet claimsSet = builder.build();
        return claimsSet.getClaims();
    }

    /**
     *
     * @param authenticationSuccess
     * @param expiration expiration time in seconds
     * @return
     */
    JWTClaimsSet.Builder generateClaimsBuilder(AuthenticationSuccess authenticationSuccess, Integer expiration) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        builder.subject(authenticationSuccess.getUsername());

        Date now = new Date();
        builder.issueTime(now);

        if ( expiration != null ) {
            log.debug("Setting expiration to {}", expiration.toString());
            Date expirationTime = new Date(now.getTime() + (expiration * 1000));
            builder.expirationTime(expirationTime);
        }

        builder.claim("roles", authenticationSuccess.getRoles());

        log.debug("Generated claim set: {}",builder.build().toJSONObject().toString());
        return builder;
    }
}
