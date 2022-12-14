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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Optional;

/**
 * Utility class to create {@link HttpRequest.Builder} from a Micronaut HTTP Request.
 *
 * @author Sergio del Amo
 * @since 4.0.0
 */
public final class HttpRequestFactory {

    private HttpRequestFactory() {
    }

    @NonNull
    public static <I> HttpRequest.Builder builder(
        @NonNull URI uri, io.micronaut.http.HttpRequest<I> request,
        ConversionService conversionService,
        Argument<?> bodyType,
        MediaTypeCodecRegistry mediaTypeCodecRegistry
    ) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
        HttpRequest.BodyPublisher bodyPublisher = publisherForRequest(request, conversionService, bodyType, mediaTypeCodecRegistry);
        builder.method(request.getMethod().toString(), bodyPublisher);
        request.getHeaders().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (request.getContentType().isEmpty()) {
            builder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        }
        return builder;
    }

    private static <I> HttpRequest.BodyPublisher publisherForRequest(
        io.micronaut.http.HttpRequest<I> request,
        ConversionService conversionService,
        Argument<?> bodyType,
        MediaTypeCodecRegistry mediaTypeCodecRegistry
    ) {
        if (io.micronaut.http.HttpMethod.permitsRequestBody(request.getMethod())) {
            Optional<?> body = request.getBody();
            boolean hasBody = body.isPresent();
            MediaType requestContentType = request.getContentType().orElseGet(() -> MediaType.APPLICATION_JSON_TYPE);
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE) && hasBody) {
                Object bodyValue = body.get();
                if (bodyValue instanceof CharSequence) {
                    return HttpRequest.BodyPublishers.ofString(bodyValue.toString());
                } else {
                    throw new UnsupportedOperationException("Body of type [" + bodyValue.getClass() + "] as " + requestContentType + " is not yet supported");
                }
            } else if (requestContentType.equals(MediaType.MULTIPART_FORM_DATA_TYPE) && hasBody) {
                Object bodyValue = body.get();
                throw new UnsupportedOperationException("Body of type [" + bodyValue.getClass() + "] as " + requestContentType + " is not yet supported");
            } else {
                if (hasBody) {
                    Object bodyValue = body.get();
                    if (Publishers.isConvertibleToPublisher(bodyValue)) {
                        throw new UnsupportedOperationException("Body of type [" + bodyValue.getClass() + "] as " + requestContentType + " is not yet supported");
                    } else if (bodyValue instanceof CharSequence) {
                        return HttpRequest.BodyPublishers.ofString(bodyValue.toString());
                    } else if (mediaTypeCodecRegistry != null) {
                        Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                        var encoded = registeredCodec.map(codec -> {
                                if (bodyType != null && bodyType.isInstance(bodyValue)) {
                                    return codec.encode((Argument<Object>) bodyType, bodyValue);
                                } else {
                                    return codec.encode(bodyValue);
                                }
                            })
                            .orElse(null);
                        if (encoded != null) {
                            return HttpRequest.BodyPublishers.ofByteArray(encoded);
                        } else {
                            return HttpRequest.BodyPublishers.noBody();
                        }
                    }
                }
            }
        }
        return HttpRequest.BodyPublishers.noBody();
    }
}
