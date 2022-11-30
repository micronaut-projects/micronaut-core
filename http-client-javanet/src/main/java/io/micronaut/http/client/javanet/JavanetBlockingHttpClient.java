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
package io.micronaut.http.client.javanet;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * {@link io.micronaut.http.client.HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public class JavanetBlockingHttpClient implements BlockingHttpClient {

    private final URI uri;

    @Nullable
    private final HttpClientConfiguration httpClientConfiguration;

    public JavanetBlockingHttpClient(@Nullable URI uri, @Nullable HttpClientConfiguration httpClientConfiguration) {
        this.uri = uri;
        this.httpClientConfiguration = httpClientConfiguration;
    }

    @Override
    public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request,
                                              Argument<O> bodyType,
                                              Argument<E> errorType) {
        HttpRequest.Builder builder = HttpRequestFactory.builder(uri.resolve(request.getUri()), request);
        HttpRequest httpRequest = builder.build();
        try {
            HttpResponse<byte[]> httpResponse = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            return new io.micronaut.http.HttpResponse<O>() {
                @Override
                public HttpStatus getStatus() {
                    return HttpStatus.valueOf(httpResponse.statusCode());
                }

                @Override
                public int code() {
                    return httpResponse.statusCode();
                }

                @Override
                public String reason() {
                    throw new UnsupportedOperationException("Not implemented yet");
                }

                @Override
                public HttpHeaders getHeaders() {
                    return new HttpHeadersAdapter(httpResponse.headers());
                }

                @Override
                public MutableConvertibleValues<Object> getAttributes() {
                    return null;
                }

                @Override
                public Optional<O> getBody() {
                    return Optional.empty();
                }
            };
        } catch (IOException | InterruptedException e) {
            throw new HttpClientException("error sending request", e);
        }
    }

    @Override
    public void close() throws IOException {

    }
}
