package io.micronaut.docs.server.callbacks

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "ReportRoutesTest")
@Singleton
class Reports {
    private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val outcomes = ConcurrentHashMap<String, CompletableFuture<Optional<Throwable>>>()

    fun generate(id: String): CompletableFuture<String> = pending.computeIfAbsent(id) { CompletableFuture() }

    fun completed(id: String, failure: Throwable?) {
        outcome(id).complete(Optional.ofNullable(failure))
    }

    fun outcome(id: String): CompletableFuture<Optional<Throwable>> = outcomes.computeIfAbsent(id) { CompletableFuture() }
}
