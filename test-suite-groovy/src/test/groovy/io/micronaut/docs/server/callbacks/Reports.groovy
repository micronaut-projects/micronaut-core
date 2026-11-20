package io.micronaut.docs.server.callbacks

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Requires(property = "spec.name", value = "ReportRoutesSpec")
@Singleton
class Reports {
    final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>()
    final Map<String, CompletableFuture<Optional<Throwable>>> outcomes = new ConcurrentHashMap<>()

    CompletableFuture<String> generate(String id) {
        pending.computeIfAbsent(id) { new CompletableFuture<String>() }
    }

    void completed(String id, Throwable failure) {
        outcome(id).complete(Optional.ofNullable(failure))
    }

    CompletableFuture<Optional<Throwable>> outcome(String id) {
        outcomes.computeIfAbsent(id) { new CompletableFuture<Optional<Throwable>>() }
    }
}
