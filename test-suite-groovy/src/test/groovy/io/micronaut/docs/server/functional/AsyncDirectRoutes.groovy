package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.web.router.builder.DirectContext
import io.micronaut.web.router.builder.DirectRouteBuilder
import io.micronaut.web.router.builder.HttpDirectRoutes
import jakarta.inject.Singleton

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Function
// end::imports[]

@Requires(property = "spec.name", value = "AsyncDirectRoutesSpec")
// tag::clazz[]
@Singleton
class AsyncDirectRoutes implements HttpDirectRoutes {

    private static final Map<String, String> REPORTS = ["2026-q1": "Q1: 1200 orders"]
    private static final Map<String, String> QUOTES = ["MNT": "42.00"]

    @Override
    void routes(DirectRouteBuilder routes) {
        routes.GET("/reports/{id}")
            .executeOn(TaskExecutors.BLOCKING) // <1>
            .respond({ DirectContext direct ->
            String report = loadReport(direct.pathVariables().getString("id"))
            report == null ? null : direct.responses().ok(report)
        } as Function<DirectContext, HttpResponse<?>>) // <2>
        routes.GET("/quotes/{symbol}").respondAsync({ DirectContext direct ->
            fetchQuote(direct.pathVariables().getString("symbol")) // <3>
                .thenApply { String quote -> quote == null ? null : direct.responses().ok(quote) } // <4>
        } as Function<DirectContext, CompletionStage<HttpResponse<?>>>)
    }

    /**
     * Blocks, e.g. on a database.
     */
    private static String loadReport(String id) {
        REPORTS[id]
    }

    /**
     * Completes later, e.g. with the response of a non-blocking client.
     */
    private static CompletionStage<String> fetchQuote(String symbol) {
        CompletableFuture.supplyAsync { QUOTES[symbol] }
    }
}
// end::clazz[]
