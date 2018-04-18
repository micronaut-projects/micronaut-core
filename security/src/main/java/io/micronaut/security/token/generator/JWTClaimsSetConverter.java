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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class JWTClaimsSetConverter implements TypeConverter<Map<String, Object>, JWTClaimsSet> {

    @Override
    public Optional<JWTClaimsSet> convert(Map<String, Object> claims, Class<JWTClaimsSet> targetType, ConversionContext context) {

        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        for ( String k : claims.keySet() ) {
            builder.claim(k, claims.get(k));
        }
        return Optional.of(builder.build());
    }
}
