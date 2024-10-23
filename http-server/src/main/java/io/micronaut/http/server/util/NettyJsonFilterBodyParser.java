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
package io.micronaut.http.server.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.filter.bodyparser.JsonFilterBodyParser;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Singleton
@Requires(bean = JsonMapper.class)
@Requires(missingBeans = JsonFilterBodyParser.class)
@Experimental
public class NettyJsonFilterBodyParser<T> implements JsonFilterBodyParser<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyJsonFilterBodyParser.class);
    private final JsonMapper jsonMapper;

    public NettyJsonFilterBodyParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Publisher<T> parseBody(HttpRequest<?> request, Class<T> type) {
        if (!supportsRequestContentType(request)) {
            return Publishers.empty();
        }
        if (request instanceof ServerHttpRequest<?> serverHttpRequest) {
            return parseBodyInServerHttpRequest(serverHttpRequest, type);
        }
        return Publishers.empty();
    }

    private Mono<T> parseBodyInServerHttpRequest(@NonNull ServerHttpRequest<?> request, Class<T> type) {
        try (CloseableByteBody closeableByteBody = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)) {
            return Mono.fromFuture(closeableByteBody.buffer())
                    .mapNotNull(bb -> {
                        try {
                            return jsonMapper.readValue(bb.toByteArray(), type);
                        } catch (IOException e) {
                            if (LOG.isErrorEnabled()) {
                                LOG.error("could not bind JSON body to {}", type);
                            }
                            return null;
                        }
                    });
        }
    }
}
