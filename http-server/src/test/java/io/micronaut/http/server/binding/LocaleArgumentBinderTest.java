/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link Locale} argument is bound to the locale of the request, or to the configured default
 * locale when the request has none.
 */
class LocaleArgumentBinderTest {

    @Test
    void bindsTheLocaleOfTheRequest() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.server.locale-resolution.default-locale", "de-DE"))) {
            BindingResult<Locale> result = bind(ctx, HttpRequest.GET("/").header(HttpHeaders.ACCEPT_LANGUAGE, "en-GB"));

            assertTrue(result.isPresentAndSatisfied());
            assertEquals(Optional.of(Locale.UK), result.getValue());
        }
    }

    @Test
    void bindsTheDefaultLocaleWhenTheRequestHasNone() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.server.locale-resolution.default-locale", "de-DE"))) {
            BindingResult<Locale> result = bind(ctx, HttpRequest.GET("/"));

            assertTrue(result.isPresentAndSatisfied());
            assertEquals(Optional.of(Locale.GERMANY), result.getValue());
        }
    }

    private static BindingResult<Locale> bind(ApplicationContext ctx, HttpRequest<?> request) {
        LocaleArgumentBinder binder = ctx.getBean(LocaleArgumentBinder.class);
        return binder.bind(ConversionContext.of(Locale.class), request);
    }
}
