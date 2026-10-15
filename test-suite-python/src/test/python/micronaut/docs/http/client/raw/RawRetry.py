# tag::imports[]
import java

from micronaut.http import HttpRequest
from micronaut.http.body import CloseableByteBody
from micronaut.http.client import AsyncRawHttpClient, RawRequestOptions
from micronaut.http.client.exceptions import UnprocessedRequestException

CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
CompletionException = java.type("java.util.concurrent.CompletionException")
CompletionStage = java.type("java.util.concurrent.CompletionStage")
Duration = java.type("java.time.Duration")
# end::imports[]


# tag::class[]
OPTIONS = (
    RawRequestOptions.proxy().toBuilder()
    .readIdleTimeout(Duration.ofMinutes(5))  # <1>
    .returnUnsentBody(True)  # <2>
    .build()
)


class RawRetry:
    def __init__(
        self,
        primary: AsyncRawHttpClient,
        fallback: AsyncRawHttpClient,
    ) -> None:
        self.primary = primary
        self.fallback = fallback

    def exchange(self, request: HttpRequest, body: CloseableByteBody) -> CompletionStage:
        def retry(response, error):
            cause = error.getCause() if isinstance(error, CompletionException) else error
            if isinstance(cause, UnprocessedRequestException):
                unsent = cause.takeUnsentBody()  # <3>
                if unsent.isPresent():
                    return self.fallback.exchange(request, unsent.get(), OPTIONS)  # <4>
            if cause is not None:
                return CompletableFuture.failedFuture(cause)
            return CompletableFuture.completedFuture(response)

        return (
            self.primary.exchange(request, body, OPTIONS)
            .handle(retry)
            .thenCompose(lambda stage: stage)
        )
# end::class[]
