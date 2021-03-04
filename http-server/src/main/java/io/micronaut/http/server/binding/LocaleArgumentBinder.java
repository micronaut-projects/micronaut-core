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
package io.micronaut.http.server.binding;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.server.util.locale.HttpLocaleResolver;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.Optional;

/**
 * Binds {@link java.util.Locale} arguments in controller methods using
 * the {@link io.micronaut.core.util.LocaleResolver}.
 *
 * @author James Kleeh
 * @since 2.3.0
 */
@Singleton
public class LocaleArgumentBinder implements TypedRequestArgumentBinder<Locale> {

    private final HttpLocaleResolver localeResolver;

    /**
     * @param localeResolver The locale resolver
     */
    public LocaleArgumentBinder(HttpLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @Override
    public Argument<Locale> argumentType() {
        return Argument.of(Locale.class);
    }

    @Override
    public BindingResult<Locale> bind(ArgumentConversionContext<Locale> context, HttpRequest<?> source) {
        final Optional<Locale> locale = localeResolver.resolve(source);
        if (locale.isPresent()) {
            return () -> locale;
        } else {
            return BindingResult.UNSATISFIED;
        }
    }
}
