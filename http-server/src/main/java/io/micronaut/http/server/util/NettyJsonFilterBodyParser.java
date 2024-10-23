package io.micronaut.http.server.util;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.filter.bodyparser.JsonFilterBodyParser;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Objects;

@Singleton
@Requires(bean = JsonMapper.class)
@Requires(missingBeans = JsonFilterBodyParser.class)
public class NettyJsonFilterBodyParser<T> implements JsonFilterBodyParser<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NettyJsonFilterBodyParser.class);
    private final JsonMapper jsonMapper;

    public NettyJsonFilterBodyParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Publisher<T> parseBody(HttpRequest<?> request, Class<T> type) {
        if (request.getContentType().isEmpty()) {
            return Publishers.empty();
        }
        if (!request.getContentType().get().equals(getContentType())) {
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
