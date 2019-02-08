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
package io.micronaut.views.model.security

import groovy.transform.CompileStatic
import io.micronaut.security.authentication.UserDetails

@CompileStatic
class UserDetailsEmail extends UserDetails  {
    String email

    UserDetailsEmail(String username, Collection<String> roles) {
        super(username, roles)
    }

    UserDetailsEmail(String username, Collection<String> roles, String email) {
        super(username, roles)
        this.email = email
    }

    @Override
    Map<String, Object> getAttributes() {
        Map<String, Object> result = super.getAttributes()
        result.put("email", email)
        result
    }
}
