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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Date;

/**
 * Validate JWT is not expired.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
@Requires(property = JwtClaimsValidator.PREFIX + ".expiration", notEquals = StringUtils.FALSE)
public class ExpirationJwtClaimsValidator implements GenericJwtClaimsValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ExpirationJwtClaimsValidator.class);

    @Override
    public boolean validate(JWTClaimsSet claimsSet) {
        final Date expTime = claimsSet.getExpirationTime();
        if (expTime != null) {
            final Date now = new Date();
            if (expTime.before(now)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("JWT token has expired");
                }
                return false;
            }
        }
        return true;
    }
}
