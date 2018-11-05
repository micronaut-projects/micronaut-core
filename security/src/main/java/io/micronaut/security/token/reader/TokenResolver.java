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

package io.micronaut.security.token.reader;

import io.micronaut.http.HttpRequest;

import java.util.Optional;

/**
 * Returns the first found token in every available {@link io.micronaut.security.token.reader.TokenReader}.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public interface TokenResolver {

    /**
     *
     * @param request The current HTTP request.
     * @return the first found token in the supplied request. Empty if none found.
     */
    Optional<String> findFirstToken(HttpRequest<?> request);
}
