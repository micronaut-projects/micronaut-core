/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Optional;

/**
 * A response of the JDK client whose streamed body was read into an array.
 *
 * @param response The response, whose body was read
 * @param body     The bytes of the body
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record BufferedJdkResponse(HttpResponse<?> response, byte[] body) implements HttpResponse<byte[]> {
    @Override
    public int statusCode() {
        return response.statusCode();
    }

    @Override
    public HttpRequest request() {
        return response.request();
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return response.headers();
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return response.sslSession();
    }

    @Override
    public URI uri() {
        return response.uri();
    }

    @Override
    public HttpClient.Version version() {
        return response.version();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof BufferedJdkResponse other && response.equals(other.response) && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return 31 * response.hashCode() + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return "BufferedJdkResponse[response=" + response + ", body=" + body.length + " bytes]";
    }
}
