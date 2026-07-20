package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.*
import io.micronaut.http.annotation.*
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.DefaultRouteBuilder
import io.micronaut.web.router.Router
import io.micronaut.web.router.RouteBuilder.UriNamingStrategy
import io.micronaut.context.ExecutionHandleLocator
import io.micronaut.context.annotation.Executable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import jakarta.inject.Singleton

class QueryMethodSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'QueryMethodSpec'])
    @Shared HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
    @Shared QueryClient queryClient = embeddedServer.applicationContext.getBean(QueryClient)
    @Shared Router router = embeddedServer.applicationContext.getBean(Router)

    void "test query method route and declarative client"() {
        when:
        HttpResponse<String> resp = client.toBlocking().exchange(
                HttpRequest.QUERY("/query-test/search", "my-query"), String
        )

        then:
        resp.status() == HttpStatus.OK
        resp.body() == 'received: my-query'
        resp.header(HttpHeaders.ACCEPT_QUERY) == 'application/x-www-form-urlencoded, application/json'

        when:
        String result = queryClient.search("other-query")

        then:
        result == 'received: other-query'

        when:
        HttpResponse<String> resp2 = client.toBlocking().exchange(
                HttpRequest.QUERY("/query-test", "convention-query"), String
        )

        then:
        resp2.status() == HttpStatus.OK
        resp2.body() == 'convention: convention-query'

        when:
        HttpResponse<String> resp3 = client.toBlocking().exchange(
                HttpRequest.QUERY("/query-test/redirect-301", "redirect-query-1"), String
        )

        then:
        resp3.status() == HttpStatus.OK
        resp3.body() == 'received: redirect-query-1'

        when:
        HttpResponse<String> resp4 = client.toBlocking().exchange(
                HttpRequest.QUERY("/query-test/redirect-302", "redirect-query-2"), String
        )

        then:
        resp4.status() == HttpStatus.OK
        resp4.body() == 'received: redirect-query-2'
    }

    @Requires(property = 'spec.name', value = 'QueryMethodSpec')
    @Controller("/query-test")
    static class QueryController {

        @Query("/search")
        HttpResponse<String> search(@Body String query) {
            return HttpResponse.ok("received: " + query)
                    .header(HttpHeaders.ACCEPT_QUERY, "application/x-www-form-urlencoded, application/json")
        }

        // Mapping by naming convention (method name is 'query')
        @Executable
        HttpResponse<String> query(@Body String body) {
            return HttpResponse.ok("convention: " + body)
        }

        @Query("/redirect-301")
        HttpResponse<?> redirect301() {
            return HttpResponse.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, "/query-test/search")
        }

        @Query("/redirect-302")
        HttpResponse<?> redirect302() {
            return HttpResponse.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/query-test/search")
        }
    }

    @Requires(property = 'spec.name', value = 'QueryMethodSpec')
    @Client("/query-test")
    static interface QueryClient {
        @Query("/search")
        String search(@Body String query)
    }

    @Requires(property = 'spec.name', value = 'QueryMethodSpec')
    @Singleton
    static class QueryRouteBuilder extends DefaultRouteBuilder {
        QueryRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
            super(executionHandleLocator, uriNamingStrategy)
            QUERY("/query-test", QueryController, "query", String)
        }
    }
}
