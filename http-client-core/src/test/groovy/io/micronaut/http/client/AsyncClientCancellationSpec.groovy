package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AsyncClientCancellationSpec extends Specification {
    void "original exchange or retrieve cancellation and timeout abort the subscription"(boolean retrieve, boolean timeout) {
        given:
        boolean cancelled = false
        HttpClient client = Mock()
        client.exchange(_, _, _) >> Mono.never().doOnCancel { cancelled = true }
        def async = new DefaultAsyncOverReactiveHttpClient(client)
        def original = (retrieve ? async.retrieve(HttpRequest.GET("/"), String) : async.exchange(HttpRequest.GET("/"), String)).toCompletableFuture()

        when:
        if (timeout) {
            try {
                original.orTimeout(1, TimeUnit.MILLISECONDS).join()
            } catch (CompletionException ignored) {
            }
        } else {
            original.cancel(false)
        }

        then:
        new PollingConditions(timeout: 5).eventually { assert cancelled }
        original.isCompletedExceptionally()
        !timeout || thrownCause(original) instanceof TimeoutException

        where:
        retrieve | timeout
        false    | false
        false    | true
        true     | false
        true     | true
    }

    void "a dependent client stage does not own cancellation or timeout"(boolean timeout) {
        given:
        CompletableFuture<HttpResponse<String>> source = new CompletableFuture<>()
        HttpClient client = Mock()
        client.exchange(_, _, _) >> Mono.fromFuture(source)
        def original = new DefaultAsyncOverReactiveHttpClient(client).retrieve(HttpRequest.GET("/"), String).toCompletableFuture()
        def dependent = original.thenApply { it.length() }

        when:
        if (timeout) {
            dependent.completeExceptionally(new TimeoutException())
        } else {
            dependent.cancel(false)
        }

        then:
        !source.isDone()
        !original.isDone()

        when:
        source.complete(HttpResponse.ok("ok"))

        then:
        original.join() == "ok"

        where:
        timeout << [false, true]
    }

    private static Throwable thrownCause(CompletableFuture<?> future) {
        try {
            future.join()
            throw new AssertionError("Expected exceptional completion")
        } catch (CompletionException e) {
            return e.cause
        }
    }
}
