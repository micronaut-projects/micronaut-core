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
package io.micronaut.http.server.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;

import java.util.Locale;
import java.util.Optional;

/**
 * Responsible for determining the current locale.
 *
 * @author James Kleeh
 * @since 2.3.0
 */
public interface LocaleResolver extends Ordered {

    /**
     * Resolves the locale for the given request.
     *
     * @param request The request
     * @return The locale
     */
    Optional<Locale> resolve(@NonNull HttpRequest<?> request);

    Locale resolveOrDefault(@NonNull HttpRequest<?> request);
}
