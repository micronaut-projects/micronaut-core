package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.direct.DirectRouteBuilder;
import io.micronaut.web.router.direct.HttpDirectRoutes;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
// end::imports[]

@Requires(property = "spec.name", value = "AsyncDirectRoutesTest")
// tag::clazz[]
@Singleton
public class AsyncDirectRoutes implements HttpDirectRoutes {

    private static final Map<String, String> REPORTS = Map.of("2026-q1", "Q1: 1200 orders");
    private static final Map<String, String> QUOTES = Map.of("MNT", "42.00");

    @Override
    public void routes(DirectRouteBuilder routes) {
        routes.GET("/reports/{id}")
            .executeOn(TaskExecutors.BLOCKING) // <1>
            .respond(direct -> {
                String report = loadReport(direct.pathVariables().getString("id"));
                return report == null ? null : direct.responses().ok(report);
            }); // <2>
        routes.GET("/quotes/{symbol}").respondAsync(direct -> fetchQuote(direct.pathVariables().getString("symbol")) // <3>
            .thenApply(quote -> quote == null ? null : direct.responses().ok(quote))); // <4>
    }

    /**
     * Blocks, e.g. on a database.
     */
    private String loadReport(String id) {
        return REPORTS.get(id);
    }

    /**
     * Completes later, e.g. with the response of a non-blocking client.
     */
    private CompletionStage<String> fetchQuote(String symbol) {
        return CompletableFuture.supplyAsync(() -> QUOTES.get(symbol));
    }
}
// end::clazz[]
