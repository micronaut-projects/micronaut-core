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

package io.micronaut.security.token;

import io.micronaut.security.token.config.TokenConfiguration;

import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link RolesFinder}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class DefaultRolesFinder implements RolesFinder {

    private TokenConfiguration tokenConfiguration;

    /**
     * Constructs a Roles Parser.
     * @param tokenConfiguration General Token Configuration
     */
    public DefaultRolesFinder(TokenConfiguration tokenConfiguration) {
        this.tokenConfiguration = tokenConfiguration;
    }

    @Override
    @Nonnull
    public List<String> findInClaims(@Nonnull Claims claims) {
        List<String> roles = new ArrayList<>();
        Object rolesObject = claims.get(tokenConfiguration.getRolesName());
        if (rolesObject != null) {
            if (rolesObject instanceof Iterable) {
                for (Object o : ((Iterable) rolesObject)) {
                    roles.add(o.toString());
                }
            } else {
                roles.add(rolesObject.toString());
            }
        }
        return roles;
    }
}
