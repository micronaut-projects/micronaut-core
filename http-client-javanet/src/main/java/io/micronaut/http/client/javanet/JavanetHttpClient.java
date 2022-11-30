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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@link HttpClient} implementation for {@literal java.net.http.*} HTTP Client.
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
public class JavanetHttpClient implements HttpClient {
    private final URI uri;

    @Nullable
    private final HttpClientConfiguration httpClientConfiguration;
    @Nullable
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;

    public JavanetHttpClient(@Nullable URI uri) {
        this(uri, null, null);
    }

    public JavanetHttpClient(
        @Nullable URI uri,
        @Nullable HttpClientConfiguration httpClientConfiguration,
        @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry
        ) {
        this.uri = uri;
        this.httpClientConfiguration = httpClientConfiguration;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return new JavanetBlockingHttpClient(uri, httpClientConfiguration, mediaTypeCodecRegistry);
    }

    @Override
    public <I, O, E> Publisher<HttpResponse<O>> exchange(@NonNull HttpRequest<I> request, @NonNull Argument<O> bodyType, @NonNull Argument<E> errorType) {
        java.net.http.HttpRequest.Builder builder = HttpRequestFactory.builder(uri.resolve(request.getUri()), request);
        java.net.http.HttpRequest httpRequest = builder.build();
        CompletableFuture<java.net.http.HttpResponse<byte[]>> completableHttpResponse = java.net.http.HttpClient.newHttpClient().sendAsync(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        CompletableFuture<HttpResponse<O>> response = completableHttpResponse.thenApply(netResponse -> getConvertedResponse(netResponse, bodyType));
        return Publishers.fromCompletableFuture(response);
    }

    <O> HttpResponse<O> getConvertedResponse(java.net.http.HttpResponse<byte[]> httpResponse, @NonNull Argument<O> bodyType) {
        return new HttpResponse<O>() {
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
                return convertBytes(getContentType(), httpResponse.body(), bodyType);
            }
        };
    }

    private <T> Optional convertBytes(Optional<MediaType> contentType, byte[] bytes, Argument<T> type) {
        boolean hasContentType = contentType.isPresent();
        if (mediaTypeCodecRegistry != null && hasContentType) {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                Charset charset = contentType.flatMap(MediaType::getCharset).orElse(StandardCharsets.UTF_8);
                return Optional.of(new String(bytes, charset));
            } else if (type.getType() == byte[].class) {
                return Optional.of(bytes);
            } else {
                Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType.get());
                if (foundCodec.isPresent()) {
                    MediaTypeCodec codec = foundCodec.get();
                    return Optional.of(codec.decode(type, bytes));
                }
            }
        }
        // last chance, try type conversion
        return ConversionService.SHARED.convert(bytes, ConversionContext.of(type));
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
