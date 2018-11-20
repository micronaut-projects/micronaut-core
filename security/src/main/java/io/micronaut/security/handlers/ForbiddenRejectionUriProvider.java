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

package io.micronaut.security.handlers;

import java.util.Optional;

/**
 *
 * Provides a uri to redirect to when an authenticated user tries to access a resource for which he does not have the
 * required authorization level.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public interface ForbiddenRejectionUriProvider {

    /**
     *
     * @return A uri to redirect to when an authenticated user tries to access a resource for which he does not have the required authorization level.
     */
    Optional<String> getForbiddenRedirectUri();
}
