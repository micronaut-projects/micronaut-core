package io.micronaut.docs.server.routes

import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.core.convert.ConversionService


// tag::imports[]
import io.micronaut.web.router.GroovyRouteBuilder

import javax.inject.Inject
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class MyRoutes extends GroovyRouteBuilder { // <1>

    MyRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService)
    }

    @Inject
    void issuesRoutes(IssuesController issuesController) { // <2>
        GET("/issues/show/{number}", issuesController.&issue) // <3>
    }
}
// end::class[]
