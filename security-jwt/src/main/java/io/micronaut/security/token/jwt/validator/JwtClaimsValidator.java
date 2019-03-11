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

import io.micronaut.security.token.jwt.config.JwtConfigurationProperties;
import io.micronaut.security.token.jwt.generator.claims.JwtClaims;

/**
 * Provides a contract to create custom JWT claims validations.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public interface JwtClaimsValidator {

    String PREFIX = JwtConfigurationProperties.PREFIX + ".claims-validators";

    /**
     *
     * @param claims JWT Claims
     * @return whether the JWT claims pass validation.
     */
    boolean validate(JwtClaims claims);
}
