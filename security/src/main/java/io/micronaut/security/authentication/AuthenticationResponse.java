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

package io.micronaut.security.authentication;

import java.io.Serializable;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface AuthenticationResponse extends Serializable {

    /**
     * Defaults to false.
     * @return true or false depending on whether the user is authenticated
     */
    default boolean isAuthenticated() {
        return false;
    }

    /**
     * @return A message if the response chose to include one
     */
    Optional<String> getMessage();

}
