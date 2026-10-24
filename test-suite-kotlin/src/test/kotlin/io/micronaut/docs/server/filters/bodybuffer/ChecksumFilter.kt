package io.micronaut.docs.server.filters.bodybuffer

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.body.AsyncRequestBody
import io.micronaut.http.server.annotation.PreMatching
import org.jspecify.annotations.Nullable
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CompletionStage
// end::imports[]

@Requires(property = "spec.name", value = "ChecksumFilterSpec")
// tag::clazz[]
@ServerFilter("/checksum")
class ChecksumFilter {

    @RequestFilter
    @PreMatching
    fun verify(request: HttpRequest<*>): CompletionStage<@Nullable HttpResponse<*>?> {
        val expected = request.headers["X-Content-SHA256"]
        return AsyncRequestBody.of(request) // <1>
            .copy()
            .bytes(1024 * 1024) // <2>
            .thenApply<HttpResponse<*>?> { bytes ->
                if (sha256(bytes) == expected) null // <3>
                else HttpResponse.badRequest("Checksum mismatch") // <4>
            }
    }

    private fun sha256(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
// end::clazz[]
