package io.micronaut.docs.server.body;

import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.CloseableInboundByteBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

@ServerFilter
public class BodyLogFilter {
    private static final Logger LOG = LoggerFactory.getLogger(BodyLogFilter.class);

    @RequestFilter
    public void logBody(ServerHttpRequest<?> request) {
        try (CloseableInboundByteBody ourCopy = request.byteBody().split()) {
            Flux.from(ourCopy.toByteArrayPublisher())
                .subscribe(array -> {
                    LOG.info("");
                });
        }
    }
}
