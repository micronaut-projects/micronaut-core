/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.type.Argument;
import io.micronaut.http.form.FormUrlEncodedDecoder;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Implementation of {@link FilterBodyParser} which leverages {@link ServerHttpRequest#byteBody()} API.
 * For form-url-encoded payloads it uses the {@link FormUrlEncodedDecoder} API.
 * For JSON payloads it uses a {@link JsonMapper}.
 * @author Sergio del Amo
 * @since 4.11.0
 */
@Requires(beans = {JsonMapper.class, FormUrlEncodedDecoder.class})
@Singleton
@Experimental
final class DefaultFilterBodyParser implements FilterBodyParser {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultFilterBodyParser.class);
    private static final @NonNull Argument<Map<String, Object>> MAP_STRING_OBJECT_ARGUMENT = Argument.mapOf(String.class, Object.class);
    private final JsonMapper jsonMapper;
    private final FormUrlEncodedDecoder formUrlEncodedDecoder;

    /**
     * @param jsonMapper JSON Mapper
     * @param formUrlEncodedDecoder Decoder for form-url-encoded payload
     */
    DefaultFilterBodyParser(FormUrlEncodedDecoder formUrlEncodedDecoder,
                            JsonMapper jsonMapper) {
        this.formUrlEncodedDecoder = formUrlEncodedDecoder;
        this.jsonMapper = jsonMapper;
    }

    @Override
    @NonNull
    @SingleResult
    public CompletableFuture<Map<String, Object>> parseBody(@NonNull HttpRequest<?> request) {
        Optional<MediaType> mediaTypeOptional = request.getContentType();
        if (mediaTypeOptional.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not parse body into a Map because the request does not have a Content-Type");
            }
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        if (!(request instanceof ServerHttpRequest<?>)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not parse body into a Map because the request is not an instance of ServerHttpRequest");
            }
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        MediaType contentType = mediaTypeOptional.get();
        if (contentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
            return parseFormUrlEncoded((ServerHttpRequest<?>) request);
        } else if (contentType.equals(MediaType.APPLICATION_JSON_TYPE)) {
            return parseJson((ServerHttpRequest<?>) request);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Could not parse body into a Map because the request's content type is not either application/x-www-form-urlencoded or application/json");
        }
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    private CompletableFuture<Map<String, Object>> parseJson(@NonNull ServerHttpRequest<?> request) {
        try (CloseableByteBody closeableByteBody = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)) {
            return closeableByteBody.buffer()
                .thenApply(bb -> {
                    try {
                        return jsonMapper.readValue(bb.toByteArray(), MAP_STRING_OBJECT_ARGUMENT);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
        }
    }

    private CompletableFuture<Map<String, Object>> parseFormUrlEncoded(@NonNull ServerHttpRequest<?> request) {
        try (CloseableByteBody closeableByteBody = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)) {
            return closeableByteBody.buffer()
                    .thenApply(bb -> bb.toString(request.getCharacterEncoding()))
                    .thenApply(str -> formUrlEncodedDecoder.decode(str, request.getCharacterEncoding()));
        }
    }
}
