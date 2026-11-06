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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.bind.binders.PostponedRequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The locale and the character encoding of the conversion contexts that the binders of the arguments of a controller
 * method receive: the ones of the request when the arguments are bound.
 */
class ArgumentConversionContextTest {
    private static final String SPEC_NAME = "ArgumentConversionContextTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void eachArgumentReceivesTheLocaleAndTheCharsetOfTheRequest() throws Exception {
        assertEquals("de-DE ISO-8859-1|de-DE ISO-8859-1",
            get("/conversion-context/two", Map.of(HttpHeaders.ACCEPT_LANGUAGE, "de-DE", HttpHeaders.ACCEPT_CHARSET, "ISO-8859-1")));
    }

    @Test
    void theContextHasTheDefaultLocaleAndUtf8WithoutHeaders() throws Exception {
        assertEquals(Locale.getDefault().toLanguageTag() + " UTF-8", get("/conversion-context/one", Map.of()));
    }

    @Test
    void theCharsetOfTheContentTypeIsTheCharsetOfTheContext() throws Exception {
        assertEquals("fr-FR UTF-16", get("/conversion-context/one",
            Map.of(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR", HttpHeaders.CONTENT_TYPE, "text/plain;charset=UTF-16")));
    }

    @Test
    void theArgumentsOfARouteWithAPostponedArgumentReceiveTheLocaleOfTheRequestAfterTheFilters() throws Exception {
        // the arguments are bound after a filter changed the Accept-Language header
        assertEquals("it-IT ISO-8859-1|it-IT ISO-8859-1",
            get("/conversion-context/postponed", Map.of(HttpHeaders.ACCEPT_LANGUAGE, "de-DE", HttpHeaders.ACCEPT_CHARSET, "ISO-8859-1",
                "X-Filter-Locale", "it-IT")));
    }

    @Test
    void anArgumentNextToABodyReceivesTheLocaleOfTheRequest() throws Exception {
        java.net.http.HttpResponse<String> response = client.send(java.net.http.HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.getPort() + "/conversion-context/body"))
                .header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("hello")).build(),
            BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("hello es-ES UTF-8", response.body());
    }

    @Test
    void primitiveValuesOfQueryValuesHeadersAndPathVariablesAreBound() throws Exception {
        assertEquals("7 8 9 true", get("/conversion-context/primitives/7?query=8&flag=true", Map.of("X-Number", "9")));
    }

    @Test
    void anInvalidPrimitiveValueIsABadRequest() throws Exception {
        assertEquals(400, status("/conversion-context/primitives/7?query=eight&flag=true", Map.of("X-Number", "9")));
        assertEquals(400, status("/conversion-context/primitives/7?query=8&flag=true", Map.of("X-Number", "nine")));
        assertEquals(400, status("/conversion-context/primitives/seven?query=8&flag=true", Map.of("X-Number", "9")));
    }

    private static String get(String path, Map<String, String> headers) throws Exception {
        java.net.http.HttpResponse<String> response = send(path, headers);
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private static int status(String path, Map<String, String> headers) throws Exception {
        return send(path, headers).statusCode();
    }

    private static java.net.http.HttpResponse<String> send(String path, Map<String, String> headers) throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path));
        headers.forEach(builder::header);
        return client.send(builder.build(), BodyHandlers.ofString());
    }

    /**
     * The locale and the charset of the conversion context of an argument.
     *
     * @param locale  The locale
     * @param charset The charset
     */
    record ConversionSettings(Locale locale, Charset charset) {
        @Override
        public String toString() {
            return locale.toLanguageTag() + " " + charset.name();
        }
    }

    /**
     * The locale and the charset of the conversion context of an argument bound after the filters.
     *
     * @param settings The settings
     */
    record PostponedConversionSettings(ConversionSettings settings) {
        @Override
        public String toString() {
            return settings.toString();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ConversionSettingsBinder implements TypedRequestArgumentBinder<ConversionSettings> {
        @Override
        public Argument<ConversionSettings> argumentType() {
            return Argument.of(ConversionSettings.class);
        }

        @Override
        public BindingResult<ConversionSettings> bind(ArgumentConversionContext<ConversionSettings> context, HttpRequest<?> source) {
            ConversionSettings settings = new ConversionSettings(context.getLocale(), context.getCharset());
            return () -> Optional.of(settings);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PostponedConversionSettingsBinder implements TypedRequestArgumentBinder<PostponedConversionSettings>,
        PostponedRequestArgumentBinder<PostponedConversionSettings> {
        @Override
        public Argument<PostponedConversionSettings> argumentType() {
            return Argument.of(PostponedConversionSettings.class);
        }

        @Override
        public BindingResult<PostponedConversionSettings> bind(ArgumentConversionContext<PostponedConversionSettings> context, HttpRequest<?> source) {
            PostponedConversionSettings settings = new PostponedConversionSettings(new ConversionSettings(context.getLocale(), context.getCharset()));
            return () -> Optional.of(settings);
        }
    }

    @ServerFilter("/conversion-context/postponed")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class LocaleFilter {
        @RequestFilter
        void request(MutableHttpRequest<?> request) {
            String locale = request.getHeaders().get("X-Filter-Locale");
            if (locale != null) {
                request.getHeaders().set(HttpHeaders.ACCEPT_LANGUAGE, locale);
            }
        }
    }

    @Controller("/conversion-context")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Produces(MediaType.TEXT_PLAIN)
    static class ConversionContextController {
        @Get("/one")
        String one(ConversionSettings settings) {
            return settings.toString();
        }

        @Get("/two")
        String two(ConversionSettings first, ConversionSettings second) {
            return first + "|" + second;
        }

        @Get("/postponed")
        String postponed(ConversionSettings before, PostponedConversionSettings after) {
            return before + "|" + after;
        }

        @Post("/body")
        @Consumes(MediaType.TEXT_PLAIN)
        String body(@Body String body, ConversionSettings settings) {
            return body + " " + settings;
        }

        @Get("/primitives/{path}")
        String primitives(int path, @QueryValue int query, @Header("X-Number") long number, @QueryValue boolean flag) {
            return path + " " + query + " " + number + " " + flag;
        }
    }
}
