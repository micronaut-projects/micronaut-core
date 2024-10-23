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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.filter.FilterBodyParser;
import io.netty.handler.codec.http.QueryStringDecoder;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named(NettyFormUrlEncodedBodyParserInFilter.NAME_QUALIFIER)
@Singleton
public class NettyFormUrlEncodedBodyParserInFilter implements FilterBodyParser<Map<String, Object>> {
    public static final String NAME_QUALIFIER =  MediaType.APPLICATION_FORM_URLENCODED;

    @Override
    @NonNull
    @SingleResult
    public Publisher<Map<String, Object>> parseBody(@NonNull HttpRequest<?> request) {
        if (request.getContentType().isEmpty()) {
            return Publishers.empty();
        }
        if (!request.getContentType().get().toString().equals(NAME_QUALIFIER)) {
            return Publishers.empty();
        }
        if (request instanceof ServerHttpRequest<?> serverHttpRequest) {
            return parseBody(serverHttpRequest);
        }
        return null;
    }

    private Mono<Map<String, Object>> parseBody(@NonNull ServerHttpRequest<?> request) {
        try (CloseableByteBody closeableByteBody = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST)) {
            return Mono.fromFuture(closeableByteBody.buffer())
                    .map(bb -> bb.toString(request.getCharacterEncoding()))
                    .map(NettyFormUrlEncodedBodyParserInFilter::parse);
        }
    }

    private static Map<String, Object> parse(String formUrlEncoded) {
        QueryStringDecoder decoder = new QueryStringDecoder(formUrlEncoded, false);
        Map<String, List<String>> parameters = decoder.parameters();
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return result;
    }
}
