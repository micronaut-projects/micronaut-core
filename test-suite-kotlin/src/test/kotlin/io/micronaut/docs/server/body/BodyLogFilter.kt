package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.body.ByteBody
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import java.util.Base64
// end::imports[]

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@ServerFilter("/person")
class BodyLogFilter {

    @RequestFilter
    fun logBody(request: ServerHttpRequest<*>) { // <2>
        request.byteBody()
            .split(ByteBody.SplitBackpressureMode.SLOWEST) // <3>
            .allowDiscard() // <5>
            .use { ourCopy -> // <4>
                Flux.from(ourCopy.toByteArrayPublisher()) // <6>
                    .onErrorComplete(ByteBody.BodyDiscardedException::class.java) // <7>
                    .subscribe { array -> LOG.info("Received body: {}", Base64.getEncoder().encodeToString(array)) } // <8>
            }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(BodyLogFilter::class.java)
    }
}
// end::clazz[]
