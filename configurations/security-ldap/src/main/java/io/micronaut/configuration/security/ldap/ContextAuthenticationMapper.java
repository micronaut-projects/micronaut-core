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

package io.micronaut.configuration.security.ldap;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.AuthenticationResponse;

import java.util.Set;

/**
 * Responsible for mapping the result of LDAP authentication to an {@link AuthenticationResponse}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface ContextAuthenticationMapper {

    /**
     * @param attributes The attributes in the context
     * @param username The username used to authenticate
     * @param groups The roles associated with the user
     * @return An {@link AuthenticationResponse}
     */
    AuthenticationResponse map(ConvertibleValues<Object> attributes, String username, Set<String> groups);
}
