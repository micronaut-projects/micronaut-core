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
package io.micronaut.core.util.locale;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.LocaleResolver;

import java.util.Locale;

/**
 * Provides an abstract class which implements {@link LocaleResolver} and handles default locale resolution.
 *
 * @author Sergio del Amo
 * @since 2.3.0
 * @param <T> The context object which will be used to resolve the locale
 */
public abstract class AbstractLocaleResolver<T> implements LocaleResolver<T> {

    protected final Locale defaultLocale;

    /**
     *
     * @param defaultLocale The locale to use if one cannot be found.
     */
    public AbstractLocaleResolver(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    @Override
    @NonNull
    public Locale resolveOrDefault(@NonNull T request) {
        return resolve(request).orElse(defaultLocale);
    }
}

