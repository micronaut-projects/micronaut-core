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
import io.micronaut.core.type.Argument;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * {@link io.micronaut.http.client.HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public final class JavanetBlockingHttpClient extends AbstractJavanetHttpClient implements BlockingHttpClient {

    public JavanetBlockingHttpClient(
        @Nullable URI uri,
        @Nullable HttpClientConfiguration httpClientConfiguration,
        @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry
    ) {
        super(uri, httpClientConfiguration, mediaTypeCodecRegistry);
    }

    @Override
    public <I, O, E> io.micronaut.http.HttpResponse<O> exchange(io.micronaut.http.HttpRequest<I> request,
                                              Argument<O> bodyType,
                                              Argument<E> errorType) {
        HttpRequest.Builder builder = HttpRequestFactory.builder(uri.resolve(request.getUri()), request);
        HttpRequest httpRequest = builder.build();
        try {
            HttpResponse<byte[]> httpResponse = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            return getConvertedResponse(httpResponse, bodyType);
        } catch (IOException | InterruptedException e) {
            throw new HttpClientException("error sending request", e);
        }
    }

    @Override
    public void close() throws IOException {

    }
}
