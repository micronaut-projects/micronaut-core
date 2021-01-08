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
package io.micronaut.core.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.order.Ordered;

import java.util.Locale;
import java.util.Optional;

/**
 * Responsible for determining the current locale given a context.
 *
 * @author James Kleeh
 * @since 2.3.0
 * @param <T> The context object which will be used to resolve the locale
 */
public interface LocaleResolver<T> extends Ordered {

    /**
     * Resolves the locale for the given context.
     *
     * @param context The context to retrieve the locale from
     * @return The locale
     */
    @NonNull
    Optional<Locale> resolve(@NonNull T context);

    @NonNull
    Locale resolveOrDefault(@NonNull T context);
}
