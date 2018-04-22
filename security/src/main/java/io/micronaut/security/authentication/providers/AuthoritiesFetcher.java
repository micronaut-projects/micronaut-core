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

package io.micronaut.security.authentication.providers;

import java.util.List;

/**
 * Responsible for finding granted authorities for a given user.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface AuthoritiesFetcher {

    /**
     * Returns granted roles for the given username.
     *
     * @param username e.g. admin
     * @return A list of authorities, or null if none could be found
     */
    List<String> findAuthoritiesByUsername(String username);
}
