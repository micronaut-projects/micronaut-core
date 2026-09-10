package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.CloseableByteBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
// end::imports[]

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@ServerFilter("/person")
class BodyLogFilter {
    private static final Logger LOG = LoggerFactory.getLogger(BodyLogFilter)

    @RequestFilter
    void logBody(ServerHttpRequest<?> request) { // <2>
        try (CloseableByteBody ourCopy = // <4>
                 request.byteBody()
                         .split(ByteBody.SplitBackpressureMode.SLOWEST) // <3>
                         .allowDiscard()) { // <5>
            Flux.from(ourCopy.toByteArrayPublisher()) // <6>
                    .onErrorComplete(ByteBody.BodyDiscardedException) // <7>
                    .subscribe(array -> LOG.info("Received body: {}", Base64.encoder.encodeToString(array))) // <8>
        }
    }
}
// end::clazz[]
