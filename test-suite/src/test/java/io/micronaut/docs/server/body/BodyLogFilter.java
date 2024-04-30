package io.micronaut.docs.server.body;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.CloseableInboundByteBody;
import io.micronaut.http.body.InboundByteBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Base64;
// end::imports[]

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@ServerFilter("/person")
public class BodyLogFilter {
    private static final Logger LOG = LoggerFactory.getLogger(BodyLogFilter.class);

    @RequestFilter
    public void logBody(ServerHttpRequest<?> request) { // <2>
        try (CloseableInboundByteBody ourCopy = // <4>
                 request.byteBody()
                     .split(InboundByteBody.SplitBackpressureMode.SLOWEST) // <3>
                     .allowDiscard()) { // <5>
            Flux.from(ourCopy.toByteArrayPublisher()) // <6>
                .onErrorComplete(InboundByteBody.BodyDiscardedException.class) // <7>
                .subscribe(array -> LOG.info("Received body: {}", Base64.getEncoder().encodeToString(array))); // <8>
        }
    }
}
// end::clazz[]
