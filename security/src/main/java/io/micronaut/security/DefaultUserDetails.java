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
package io.micronaut.security;

import java.util.List;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class DefaultUserDetails implements UserDetails {
    private String username;
    private List<String> roles;

    public DefaultUserDetails(String username, List<String> roles) {
        this.username = username;
        this.roles = roles;
    }
    @Override
    public List<String> getRoles() {
        return roles;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
