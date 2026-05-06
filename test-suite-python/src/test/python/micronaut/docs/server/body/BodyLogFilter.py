import java

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.http import ServerHttpRequest
from micronaut.http.annotation import RequestFilter, ServerFilter
from micronaut.http.body import ByteBody

Base64 = java.type("java.util.Base64")
Flux = java.type("reactor.core.publisher.Flux")
LoggerFactory = java.type("org.slf4j.LoggerFactory")
# end::imports[]


@Requires(property="spec.name", value="BodyLogFilterSpec")
# tag::clazz[]
@ServerFilter("/person")
class BodyLogFilter:
    LOG = LoggerFactory.getLogger("micronaut.docs.server.body.BodyLogFilter")

    @RequestFilter
    def logBody(self, request: ServerHttpRequest) -> None:  # <2>
        ourCopy = request.byteBody().split(  # <4>
            ByteBody.SplitBackpressureMode.SLOWEST  # <3>
        ).allowDiscard()  # <5>
        try:
            getattr(Flux, "from")(ourCopy.toByteArrayPublisher()).onErrorComplete(
                ByteBody.BodyDiscardedException  # <7>
            ).subscribe(
                lambda array: self.LOG.info(
                    "Received body: {}", Base64.getEncoder().encodeToString(array)
                )  # <8>
            )
        finally:
            ourCopy.close()
# end::clazz[]
