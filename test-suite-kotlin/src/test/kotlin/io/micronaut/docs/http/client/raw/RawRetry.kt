package io.micronaut.docs.http.client.raw

// tag::imports[]
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.AsyncRawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.client.exceptions.UnprocessedRequestException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
// end::imports[]

// tag::class[]
class RawRetry(
    private val primary: AsyncRawHttpClient,
    private val fallback: AsyncRawHttpClient
) {
    fun exchange(request: HttpRequest<*>, body: CloseableByteBody): CompletionStage<HttpResponse<*>> =
        primary.exchange(request, body, OPTIONS)
            .handle { response, error ->
                val cause = if (error is CompletionException) error.cause else error
                val unsent = (cause as? UnprocessedRequestException)?.takeUnsentBody()?.orElse(null) // <3>
                when {
                    unsent != null -> fallback.exchange(request, unsent, OPTIONS) // <4>
                    cause != null -> CompletableFuture.failedFuture(cause)
                    else -> CompletableFuture.completedFuture(response)
                }
            }
            .thenCompose { it }

    companion object {
        private val OPTIONS: RawRequestOptions = RawRequestOptions.proxy().toBuilder()
            .readIdleTimeout(Duration.ofMinutes(5)) // <1>
            .returnUnsentBody(true) // <2>
            .build()
    }
}
// end::class[]
