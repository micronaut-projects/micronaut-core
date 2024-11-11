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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

/**
 * Utility class to create {@link HttpRequest.Builder} from a Micronaut HTTP Request.
 *
 * @author Sergio del Amo
 * @since 4.0.0
 */
@Internal
@Experimental
public final class HttpRequestFactory {

    private HttpRequestFactory() {
    }

    @NonNull
    public static <I> HttpRequest.Builder builder(
        @NonNull URI uri, io.micronaut.http.HttpRequest<I> request,
        @NonNull HttpClientConfiguration configuration,
        @Nullable Argument<?> bodyType,
        @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry,
        @NonNull MessageBodyHandlerRegistry messageBodyHandlerRegistry
    ) {
        MutableHttpRequest<I> mutableHttpRequest = request.toMutableRequest();
        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
        configuration.getReadTimeout().ifPresent(builder::timeout);
        if (mutableHttpRequest.getMethod() == HttpMethod.GET) {
            builder.GET();
        } else {
            HttpRequest.BodyPublisher bodyPublisher = publisherForRequest(mutableHttpRequest, bodyType, mediaTypeCodecRegistry, messageBodyHandlerRegistry);
            builder.method(mutableHttpRequest.getMethod().toString(), bodyPublisher);
        }
        mutableHttpRequest.getHeaders().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (mutableHttpRequest.getContentType().isEmpty()) {
            builder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        }
        configuration.getReadTimeout().ifPresent(builder::timeout);
        return builder;
    }

    private static <I> HttpRequest.BodyPublisher publisherForRequest(
        @NonNull MutableHttpRequest<I> request,
        @Nullable Argument<?> bodyType,
        @Nullable MediaTypeCodecRegistry mediaTypeCodecRegistry,
        @NonNull MessageBodyHandlerRegistry messageBodyHandlerRegistry
    ) {
        if (request instanceof RawHttpRequestWrapper<?> raw) {
            OptionalLong length = raw.byteBody().expectedLength();
            Flow.Publisher<ByteBuffer> buffers = JdkFlowAdapter.publisherToFlowPublisher(
                Flux.from(raw.byteBody().toByteArrayPublisher()).map(ByteBuffer::wrap));
            if (length.isPresent()) {
                return HttpRequest.BodyPublishers.fromPublisher(buffers, length.getAsLong());
            } else {
                return HttpRequest.BodyPublishers.fromPublisher(buffers);
            }
        }
        if (!HttpMethod.permitsRequestBody(request.getMethod())) {
            return HttpRequest.BodyPublishers.noBody();
        }
        Optional<?> body = request.getBody();
        if (body.isPresent()) {
            Object bodyValue = body.get();
            MediaType requestContentType = request.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE);
            if (requestContentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
                if (bodyValue instanceof CharSequence) {
                    return HttpRequest.BodyPublishers.ofString(bodyValue.toString());
                }
                if (bodyValue instanceof Map<?, ?> mapBody) {
                    return HttpRequest.BodyPublishers.ofString(encodeBody(mapBody, request.getCharacterEncoding()));
                }
            }
            if (Publishers.isConvertibleToPublisher(bodyValue)) {
                throw unsupportedBodyType(bodyValue.getClass(), requestContentType.toString());
            }
            if (bodyValue instanceof CharSequence) {
                return HttpRequest.BodyPublishers.ofString(bodyValue.toString());
            }
            if (mediaTypeCodecRegistry != null) {
                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(requestContentType);
                var encoded = registeredCodec.map(codec -> {
                        if (bodyType != null && bodyType.isInstance(bodyValue)) {
                            return codec.encode((Argument<Object>) bodyType, bodyValue);
                        }
                        return codec.encode(bodyValue);
                    })
                    .orElse(null);
                if (encoded != null) {
                    return HttpRequest.BodyPublishers.ofByteArray(encoded);
                }
            }
            Argument<Object> bodyArgument = bodyType != null && bodyType.isInstance(bodyValue) ? (Argument<Object>) bodyType : Argument.ofInstance(bodyValue);
            MessageBodyWriter<Object> messageBodyWriter = messageBodyHandlerRegistry.findWriter(bodyArgument, requestContentType).orElse(null);
            if (messageBodyWriter != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                messageBodyWriter.writeTo(
                    bodyArgument,
                    requestContentType,
                    bodyValue,
                    request.getHeaders(),
                    byteArrayOutputStream
                );
                return HttpRequest.BodyPublishers.ofByteArray(byteArrayOutputStream.toByteArray());
            }
            throw unsupportedBodyType(bodyValue.getClass(), requestContentType.toString());
        }
        return HttpRequest.BodyPublishers.noBody();
    }

    private static String encodeBody(Map<?, ?> mapBody, Charset characterEncoding) {
        return mapBody
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .map(entry -> URLEncoder.encode(entry.getKey().toString(), characterEncoding) + "=" + URLEncoder.encode(entry.getValue().toString(), characterEncoding))
            .collect(Collectors.joining("&"));
    }

    private static UnsupportedOperationException unsupportedBodyType(Class<?> clazz, String contentType) {
        return new UnsupportedOperationException("Body of type [" + clazz + "] as " + contentType + " is not yet supported");
    }
}
