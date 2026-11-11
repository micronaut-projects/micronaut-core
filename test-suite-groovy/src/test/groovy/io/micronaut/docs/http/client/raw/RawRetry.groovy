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
class RawRetry {
    private static final RawRequestOptions OPTIONS = RawRequestOptions.proxy().toBuilder()
        .readIdleTimeout(Duration.ofMinutes(5)) // <1>
        .returnUnsentBody(true) // <2>
        .build()

    private final AsyncRawHttpClient primary
    private final AsyncRawHttpClient fallback

    RawRetry(AsyncRawHttpClient primary, AsyncRawHttpClient fallback) {
        this.primary = primary
        this.fallback = fallback
    }

    CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, CloseableByteBody body) {
        primary.exchange(request, body, OPTIONS)
            .handle { HttpResponse<?> response, Throwable error ->
                Throwable cause = error instanceof CompletionException ? error.cause : error
                if (cause instanceof UnprocessedRequestException) {
                    Optional<CloseableByteBody> unsent = cause.takeUnsentBody() // <3>
                    if (unsent.present) {
                        return fallback.exchange(request, unsent.get(), OPTIONS) // <4>
                    }
                }
                return cause == null ? CompletableFuture.completedFuture(response) : CompletableFuture.failedFuture(cause)
            }
            .thenCompose { it } as CompletionStage<HttpResponse<?>>
    }
}
// end::class[]
