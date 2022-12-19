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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class used to perform assertions.
 * @author Sergio del Amo
 * @since 3.8.0
 */
public final class AssertionUtils {

    private AssertionUtils() {

    }

    public static void assertThrows(@NonNull ServerUnderTest server,
                                    @NonNull HttpRequest<?> request,
                                    @NonNull HttpResponseAssertion assertion) {
        assertThrows(server, request, assertion.getHttpStatus(), assertion.getBody(), assertion.getHeaders());
    }

    public static void assertThrows(@NonNull ServerUnderTest server,
                                    @NonNull HttpRequest<?> request,
                                    @NonNull HttpStatus expectedStatus,
                                    @Nullable String expectedBody,
                                    @Nullable Map<String, String> expectedHeaders) {
        Executable e = expectedBody != null ?
            () -> server.exchange(request, String.class) :
            () -> server.exchange(request);
        HttpClientResponseException thrown = Assertions.assertThrows(HttpClientResponseException.class, e);
        HttpResponse<?> response = thrown.getResponse();
        assertEquals(expectedStatus, response.getStatus());
        assertHeaders(response, expectedHeaders);
        assertBody(response, expectedBody);
    }

    public static <T> void assertDoesNotThrow(@NonNull ServerUnderTest server,
                                          @NonNull HttpRequest<T> request,
                                          @NonNull HttpResponseAssertion assertion) {
        assertDoesNotThrow(server, request, assertion.getHttpStatus(), assertion.getBody(), assertion.getHeaders());
    }

    public static <T> void assertDoesNotThrow(@NonNull ServerUnderTest server,
                                          @NonNull HttpRequest<T> request,
                                          @NonNull HttpStatus expectedStatus,
                                          @Nullable String expectedBody,
                                          @Nullable Map<String, String> expectedHeaders) {
        ThrowingSupplier<HttpResponse<?>> executable = expectedBody != null ?
            () -> server.exchange(request, String.class) :
            () -> server.exchange(request);
        HttpResponse<?> response = Assertions.assertDoesNotThrow(executable);
        assertEquals(expectedStatus, response.getStatus());
        assertHeaders(response, expectedHeaders);
        assertBody(response, expectedBody);
    }

    private static void assertBody(@NonNull HttpResponse<?> response,  @Nullable String expectedBody) {
        if (expectedBody != null) {
            Optional<String> bodyOptional = response.getBody(String.class);
            assertTrue(bodyOptional.isPresent());
            assertTrue(bodyOptional.get().contains(expectedBody));
        }
    }

    private static void assertHeaders(@NonNull HttpResponse<?> response,  @Nullable Map<String, String> expectedHeaders) {

        if (expectedHeaders != null) {
            for (String headerName : expectedHeaders.keySet()) {
                Optional<String> headerOptional = response.getHeaders().getFirst(headerName);
                assertTrue(headerOptional.isPresent(), () -> "Header " +  headerName + " not present");
                String headerValue = headerOptional.get();
                String expectedValue = expectedHeaders.get(headerName);
                if (headerName.equals(HttpHeaders.CONTENT_TYPE)) {
                    if (headerValue.contains(";charset=")) {
                        assertTrue(headerValue.startsWith(expectedValue), () -> "header value " + headerValue + " does not start with " + expectedValue);
                    } else {
                        assertEquals(expectedValue, headerOptional.get());
                    }
                } else {
                    assertEquals(expectedValue, headerOptional.get());
                }

            }
        }
    }

}
