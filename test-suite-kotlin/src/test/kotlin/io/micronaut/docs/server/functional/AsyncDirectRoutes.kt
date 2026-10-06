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

@Requires(property = "spec.name", value = "AsyncDirectRoutesTest")
// tag::clazz[]
@Singleton
class AsyncDirectRoutes : HttpDirectRoutes {

    companion object {
        private val REPORTS = mapOf("2026-q1" to "Q1: 1200 orders")
        private val QUOTES = mapOf("MNT" to "42.00")
    }

    override fun routes(routes: DirectRouteBuilder) {
        routes.GET("/reports/{id}")
            .executeOn(TaskExecutors.BLOCKING) // <1>
            .respond(Function<DirectContext, HttpResponse<*>?> { direct ->
            val report = loadReport(direct.pathVariables().getString("id"))
            if (report == null) null else direct.responses().ok(report)
        }) // <2>
        routes.GET("/quotes/{symbol}").respondAsync(Function<DirectContext, CompletionStage<HttpResponse<*>?>> { direct ->
            fetchQuote(direct.pathVariables().getString("symbol")) // <3>
                .thenApply { quote -> if (quote == null) null else direct.responses().ok(quote) } // <4>
        })
    }

    /**
     * Blocks, e.g. on a database.
     */
    private fun loadReport(id: String): String? = REPORTS[id]

    /**
     * Completes later, e.g. with the response of a non-blocking client.
     */
    private fun fetchQuote(symbol: String): CompletionStage<String?> = CompletableFuture.supplyAsync { QUOTES[symbol] }
}
// end::clazz[]
