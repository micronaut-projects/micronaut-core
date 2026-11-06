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
package io.micronaut.web.router;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestConversionContextsTest {

    @Test
    void theContextsHaveTheLocaleAndTheCharsetOfTheRequest() {
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE")
            .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-16");
        ArgumentConversionContext<String> context = new RequestConversionContexts(request).newContext(Argument.STRING);

        assertEquals(Locale.GERMANY, context.getLocale());
        assertEquals(StandardCharsets.UTF_16, context.getCharset());
        assertSame(Argument.STRING, context.getArgument());
        assertEquals(Argument.STRING.toString(), context.toString());
    }

    @Test
    void theContextsHaveTheDefaultLocaleAndUtf8WithoutHeaders() {
        ArgumentConversionContext<String> context = new RequestConversionContexts(HttpRequest.GET("/")).newContext(Argument.STRING);

        assertEquals(Locale.getDefault(), context.getLocale());
        assertEquals(StandardCharsets.UTF_8, context.getCharset());
    }

    @Test
    void theLocaleIsReadWhenAConversionFirstNeedsIt() {
        MutableHttpRequest<?> request = HttpRequest.GET("/").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE");
        RequestConversionContexts contexts = new RequestConversionContexts(request);
        ArgumentConversionContext<String> first = contexts.newContext(Argument.STRING);
        request.getHeaders().set(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR");

        assertEquals(Locale.FRANCE, first.getLocale());
        request.getHeaders().set(HttpHeaders.ACCEPT_LANGUAGE, "it-IT");
        assertEquals(Locale.FRANCE, contexts.newContext(Argument.STRING).getLocale());
    }

    @Test
    void resolveReadsTheLocaleAndTheCharsetNow() {
        MutableHttpRequest<?> request = HttpRequest.GET("/").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE");
        RequestConversionContexts contexts = new RequestConversionContexts(request);
        ArgumentConversionContext<String> context = contexts.newContext(Argument.STRING);
        contexts.resolve();
        request.getHeaders().set(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-16");

        assertEquals(Locale.GERMANY, context.getLocale());
        assertEquals(StandardCharsets.UTF_8, context.getCharset());
    }

    @Test
    void theContextKeepsTheConversionErrors() {
        ArgumentConversionContext<Integer> context = new RequestConversionContexts(HttpRequest.GET("/")).newContext(Argument.INT);
        assertTrue(context.getLastError().isEmpty());
        assertFalse(context.iterator().hasNext());
        assertFalse(context.hasErrors());

        IllegalArgumentException first = new IllegalArgumentException("first");
        NumberFormatException second = new NumberFormatException("second");
        context.reject(first);
        context.reject("x", second);
        context.reject(null);

        assertTrue(context.hasErrors());
        ConversionError last = context.getLastError().orElseThrow();
        assertSame(second, last.getCause());
        assertEquals("x", last.getOriginalValue().orElseThrow());
        Iterator<ConversionError> errors = context.iterator();
        assertSame(first, errors.next().getCause());
        assertSame(second, errors.next().getCause());
        assertFalse(errors.hasNext());
    }

    @Test
    void contextsOfTheSameArgumentLocaleAndCharsetAreEqual() {
        RequestConversionContexts german = new RequestConversionContexts(HttpRequest.GET("/").header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE"));
        RequestConversionContexts french = new RequestConversionContexts(HttpRequest.GET("/").header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"));
        RequestConversionContexts utf16 = new RequestConversionContexts(HttpRequest.GET("/")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE")
            .header(HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-16"));
        ArgumentConversionContext<String> context = german.newContext(Argument.STRING);

        assertEquals(context, context);
        assertEquals(context, german.newContext(Argument.STRING));
        assertEquals(context.hashCode(), german.newContext(Argument.STRING).hashCode());
        assertNotEquals(context, german.newContext(Argument.of(List.class, String.class)));
        assertNotEquals(context, french.newContext(Argument.STRING));
        assertNotEquals(context, utf16.newContext(Argument.STRING));
        assertNotEquals(context, ConversionContext.of(Argument.STRING, Locale.GERMANY, StandardCharsets.UTF_8));
        assertNotEquals(null, context);
    }
}
