from micronaut.context.annotation import Requires

# tag::imports[]
import java
from micronaut.http import HttpRequest, HttpResponse
from micronaut.http.annotation import RequestFilter, ServerFilter
from micronaut.http.body import AsyncRequestBody
from micronaut.http.server.annotation import PreMatching

CompletionStage = java.type("java.util.concurrent.CompletionStage")
HexFormat = java.type("java.util.HexFormat")
MessageDigest = java.type("java.security.MessageDigest")
# end::imports[]


@Requires(property="spec.name", value="ChecksumFilterSpec")
# tag::clazz[]
@ServerFilter("/checksum")
class ChecksumFilter:
    @RequestFilter
    @PreMatching
    def verify(self, request: HttpRequest) -> CompletionStage[HttpResponse | None]:
        expected = request.getHeaders().get("X-Content-SHA256")
        return (AsyncRequestBody.of(request)  # <1>
                .copy()
                .bytes(1024 * 1024)  # <2>
                .thenApply(lambda data: None  # <3>
                           if sha256(data) == expected
                           else HttpResponse.badRequest("Checksum mismatch")))  # <4>


def sha256(data) -> str:
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data))
# end::clazz[]
