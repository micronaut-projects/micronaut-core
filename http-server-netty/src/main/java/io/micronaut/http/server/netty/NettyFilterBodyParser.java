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
package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.filter.bodyparser.FilterBodyParser;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.json.JsonMapper;
import io.netty.handler.codec.http.QueryStringDecoder;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of {@link FilterBodyParser} which leverages {@link ServerHttpRequest#byteBody()} API.
 * For form-url-encoded payloads it uses Netty {@link QueryStringDecoder}.
 * For JSON payloads it uses a {@link JsonMapper}.
 * @author Sergio del Amo
 * @since 4.11.0
 */
@Requires(bean = JsonMapper.class)
@Requires(missingBeans = FilterBodyParser.class)
@Singleton
@Experimental
final class NettyFilterBodyParser implements FilterBodyParser {
    private static final Logger LOG = LoggerFactory.getLogger(NettyFilterBodyParser.class);
    private final Integer maxParams;
    private final JsonMapper jsonMapper;


    /**
     * @param jsonMapper JSON Mapper
     * @param httpServerConfiguration HTTP Server configuration
     */
    NettyFilterBodyParser(HttpServerConfiguration httpServerConfiguration,
                          JsonMapper jsonMapper) {
        this.maxParams = httpServerConfiguration.getFilterBodyParserFormMaxParams();
        this.jsonMapper = jsonMapper;
    }

    @Override
    @NonNull
    @SingleResult
    public Publisher<Map<String, Object>> parseBody(@NonNull HttpRequest<?> request) {
        Optional<MediaType> mediaTypeOptional = request.getContentType();
        if (mediaTypeOptional.isEmpty()) {
            LOG.debug("Could not parse body into a Map because the request does not have a Content-Type");
            return Publishers.empty();
        }
        if (!(request instanceof ServerHttpRequest<?>)) {
            LOG.debug("Could not parse body into a Map because the request is not an instance of ServerHttpRequest");
            return Publishers.empty();
        }
        MediaType contentType = mediaTypeOptional.get();
        if (contentType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
            return parseFormUrlEncoded((ServerHttpRequest<?>) request);
        } else if (contentType.equals(MediaType.APPLICATION_JSON_TYPE)) {
            return parseJson((ServerHttpRequest<?>) request);
        }
        LOG.debug("Could not parse body into a Map because the request's content type is not either application/x-www-form-urlencoded or application/json");
        return Publishers.empty();
    }

        private Mono<Map<String, Object>> parseJson(@NonNull ServerHttpRequest<?> request) {
            try (CloseableByteBody closeableByteBody = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)) {
                return Mono.fromFuture(closeableByteBody.buffer())
                        .flatMap(bb -> {
                            try {
                                return Mono.just(jsonMapper.readValue(bb.toByteArray(), Argument.mapOf(String.class, Object.class)));
                            } catch (IOException e) {
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("could not bind JSON body to Map");
                                }
                                return Mono.error(e);
                            }
                        });
            }
        }

    private Mono<Map<String, Object>> parseFormUrlEncoded(@NonNull ServerHttpRequest<?> request) {
        try (CloseableByteBody closeableByteBody = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)) {
            return Mono.fromFuture(closeableByteBody.buffer())
                    .map(bb -> bb.toString(request.getCharacterEncoding()))
                    .map(str -> parse(request, str));
        }
    }

    private Map<String, Object> parse(@NonNull ServerHttpRequest<?> request, String formUrlEncoded) {
        QueryStringDecoder decoder = new QueryStringDecoder(formUrlEncoded, request.getCharacterEncoding(), false, maxParams);
        Map<String, List<String>> parameters = decoder.parameters();
        Map<String, Object> result = new LinkedHashMap<>(parameters.size());
        parameters.forEach((k, v) -> {
            if (v.size() > 1) {
                result.put(k, v);
            } else if (v.size() == 1) {
                result.put(k, v.get(0));
            } else {
                result.put(k, null);
            }
        });
        return result;
    }
}
