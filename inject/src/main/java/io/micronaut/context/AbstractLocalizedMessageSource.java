/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.LocaleResolver;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract class which implements {@link LocalizedMessageSource} and leverages {@link LocaleResolver} API.
 * @author Sergio del Amo
 * @since 3.4.0
 * @param <T> The context object which will be used to resolve the locale
 */
public abstract class AbstractLocalizedMessageSource<T> implements LocalizedMessageSource {
    private final LocaleResolver<T> localeResolver;
    private final MessageSource messageSource;

    /**
     *
     * @param localeResolver The locale resolver
     * @param messageSource The message source
     */
    public AbstractLocalizedMessageSource(LocaleResolver<T> localeResolver,
                                          MessageSource messageSource) {
        this.localeResolver = localeResolver;
        this.messageSource = messageSource;
    }

    /**
     *
     * @return The resolved locale;
     */
    @NonNull
    protected abstract Locale getLocale();

    /**
     * Resolve a message for the given code and variables for the messages.
     * @param code The code
     * @param variables to be used to interpolate the message
     * @return A message if present
     */
    @Override
    @NonNull
    public Optional<String> getMessage(@NonNull String code, Object... variables) {
        return messageSource.getMessage(code, getLocale(), variables);
    }

    /**
     * Resolve a message for the given code and variables for the messages.
     * @param code The code
     * @param variables to be used to interpolate the message
     * @return A message if present
     */
    @Override
    @NonNull
    public Optional<String> getMessage(@NonNull String code, Map<String, Object> variables) {
        return messageSource.getMessage(code, getLocale(), variables);
    }

    @Override
    @NonNull
    public Optional<String> getMessage(@NonNull String code) {
        return messageSource.getMessage(code, getLocale());
    }

    /**
     * @param localeResolutionContext The context object which will be used to resolve the locale
     * @return The resolved locale;
     */
    @NonNull
    protected Locale resolveLocale(T localeResolutionContext) {
        return localeResolver.resolveOrDefault(localeResolutionContext);
    }
}
