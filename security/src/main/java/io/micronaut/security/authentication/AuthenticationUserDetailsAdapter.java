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
package io.micronaut.security.authentication;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Adapter from {@link UserDetails} to {@link Authentication}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class AuthenticationUserDetailsAdapter implements Authentication {

    private UserDetails userDetails;

    @Nullable
    private String rolesKey;

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @param rolesKey The key name that should used to store the roles
     */
    public AuthenticationUserDetailsAdapter(UserDetails userDetails, @Nullable String rolesKey) {
        this.userDetails = userDetails;
        this.rolesKey = rolesKey;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> result = userDetails.getAttributes();
        if (rolesKey != null) {
            result.putIfAbsent(rolesKey, userDetails.getRoles());
        }
        return result;
    }

    @Override
    public String getName() {
        return userDetails.getUsername();
    }
}
