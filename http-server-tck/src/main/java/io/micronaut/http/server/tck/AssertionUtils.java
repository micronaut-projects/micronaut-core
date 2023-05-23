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
package io.micronaut.http.server.tck;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Utility class used to perform assertions.
 * @author Sergio del Amo
 * @since 3.8.0
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
})
@Experimental
public final class AssertionUtils {

    private AssertionUtils() {

    }

    public static void assertThrows(@NonNull ServerUnderTest server,
                                    @NonNull HttpRequest<?> request,
                                    @NonNull HttpResponseAssertion assertion) {
        Executable e = assertion.getBody() != null ?
            () -> server.exchange(request, String.class) :
            () -> server.exchange(request);
        HttpClientResponseException thrown = Assertions.assertThrows(HttpClientResponseException.class, e);
        HttpResponse<?> response = thrown.getResponse();
        assertEquals(assertion.getHttpStatus(), response.getStatus());
        assertHeaders(response, assertion.getHeaders());
        assertBody(response, assertion.getBody());
        assertion.getResponseConsumer().ifPresent(httpResponseConsumer -> httpResponseConsumer.accept(response));
    }

    public static void assertThrows(@NonNull ServerUnderTest server,
                                    @NonNull HttpRequest<?> request,
                                    @NonNull HttpStatus expectedStatus,
                                    @Nullable String expectedBody,
                                    @Nullable Map<String, String> expectedHeaders) {
        assertThrows(server, request, HttpResponseAssertion.builder()
            .status(expectedStatus)
            .body(expectedBody)
            .headers(expectedHeaders)
            .build());
    }

    public static <T> void assertDoesNotThrow(@NonNull ServerUnderTest server,
                                          @NonNull HttpRequest<T> request,
                                          @NonNull HttpStatus expectedStatus,
                                          @Nullable String expectedBody,
                                          @Nullable Map<String, String> expectedHeaders) {
        assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(expectedStatus)
            .body(expectedBody)
            .headers(expectedHeaders)
            .build());
    }

    public static <T> void assertDoesNotThrow(@NonNull ServerUnderTest server,
                                              @NonNull HttpRequest<T> request,
                                              @NonNull HttpResponseAssertion assertion) {
        assertDoesNotThrow(server, request, assertion.getBody() != null ? Argument.of(String.class) : null, assertion);
    }

    public static <T, B> void assertDoesNotThrow(@NonNull ServerUnderTest server,
                                              @NonNull HttpRequest<T> request,
                                              @Nullable Argument<B> bodyType,
                                              @NonNull HttpResponseAssertion assertion) {
        ThrowingSupplier<HttpResponse<?>> executable = bodyType != null ?
            () -> server.exchange(request, bodyType) :
            () -> server.exchange(request);
        HttpResponse<?> response = Assertions.assertDoesNotThrow(executable);
        assertEquals(assertion.getHttpStatus(), response.getStatus());
        assertHeaders(response, assertion.getHeaders());
        assertBody(response, assertion.getBody());
        assertion.getResponseConsumer().ifPresent(httpResponseConsumer -> httpResponseConsumer.accept(response));
    }

    private static <T> void assertBody(@NonNull HttpResponse<?> response,  @Nullable BodyAssertion<T> bodyAssertion) {
        if (bodyAssertion != null) {
            Optional<T> bodyOptional = response.getBody(bodyAssertion.getBodyType());
            assertTrue(bodyOptional.isPresent());
            bodyOptional.ifPresent(bodyAssertion::evaluate);
        }
    }

    private static void assertHeaders(@NonNull HttpResponse<?> response,  @Nullable Map<String, String> expectedHeaders) {

        if (expectedHeaders != null) {
            for (Map.Entry<String, String> expectedHeadersEntrySet : expectedHeaders.entrySet()) {
                String headerName = expectedHeadersEntrySet.getKey();
                Optional<String> headerOptional = response.getHeaders().getFirst(headerName);
                assertTrue(headerOptional.isPresent(), () -> "Header " +  headerName + " not present");
                headerOptional.ifPresent(headerValue -> {
                    String expectedValue = expectedHeadersEntrySet.getValue();
                    if (headerName.equals(HttpHeaders.CONTENT_TYPE)) {
                        if (headerValue.contains(";charset=")) {
                            assertTrue(headerValue.startsWith(expectedValue), () -> "header value " + headerValue + " does not start with " + expectedValue);
                        } else {
                            assertEquals(expectedValue, headerOptional.get());
                        }
                    } else {
                        assertEquals(expectedValue, headerOptional.get());
                    }
                });
            }

        }
    }

}
