/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.cookie;

import io.micronaut.core.convert.value.ConvertibleValues;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Models the defined {@link Cookie} instances in an application.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Cookies extends ConvertibleValues<Cookie> {

    /**
     * @return A set of the cookies
     */
    Set<Cookie> getAll();

    /**
     * Find a {@link Cookie} for the given name.
     *
     * @param name The cookie
     * @return An {@link Optional} cookie
     */
    Optional<Cookie> findCookie(CharSequence name);

    /**
     * Get a cookie by name or return null.
     *
     * @param name The name of the cookie
     * @return The Cookie instance
     */
    default Cookie get(CharSequence name) {
        return findCookie(name).orElse(null);
    }

    @Override
    default Set<String> names() {
        return getAll()
            .stream()
            .map((Cookie::getName))
            .collect(Collectors.toSet());
    }
}
