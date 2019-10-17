package io.micronaut.docs.server.routes

// tag::imports[]
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.web.router.DefaultRouteBuilder
import io.micronaut.web.router.RouteBuilder

import javax.inject.Inject
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class MyRoutes(executionHandleLocator: ExecutionHandleLocator, uriNamingStrategy: RouteBuilder.UriNamingStrategy) :
        DefaultRouteBuilder(executionHandleLocator, uriNamingStrategy) { // <1>

    @Inject
    fun issuesRoutes(issuesController: IssuesController) { // <2>
        GET("/issues/show/{number}", issuesController, "issue", Int::class.java) // <3>
    }
}
// end::class[]
