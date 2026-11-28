package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14444')
class RootQueryClientSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RootQueryClientSpec'])

    @Shared
    @AutoCleanup
    ApplicationContext clientContext = ApplicationContext.run([
            'spec.name': 'RootQueryClientSpec',
            // no path and no trailing slash
            'root.url' : "http://localhost:${server.port}".toString()
    ])

    void "a declarative client aimed at a root URL sends '/' as the path with a query"() {
        given:
        RootClient client = clientContext.getBean(RootClient)

        expect:
        client.withTemplate('x') == 'q=x'
        client.withoutTemplate('x') == 'q=x'
        client.post('x', 'body') == 'q=x body=body'
    }

    void "a low-level client sends '/' as the path when the request URI has an empty path"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, new URL("http://localhost:${server.port}"))

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("http://localhost:${server.port}?q=x")) == 'q=x'
        client.toBlocking().retrieve(HttpRequest.GET("http://localhost:${server.port}")) == 'q=null'

        cleanup:
        client.close()
    }

    @Requires(property = 'spec.name', value = 'RootQueryClientSpec')
    @Controller
    static class RootController {
        @Get
        String list(@Nullable @QueryValue String q) {
            return "q=$q"
        }

        @Post(consumes = MediaType.TEXT_PLAIN)
        String post(@Nullable @QueryValue String q, @Body String body) {
            return "q=$q body=$body"
        }
    }

    @Requires(property = 'spec.name', value = 'RootQueryClientSpec')
    @Client('${root.url}')
    static interface RootClient {
        @Get('{?q}')
        String withTemplate(@QueryValue String q)

        @Get
        String withoutTemplate(@QueryValue String q)

        @Post(value = '/', produces = MediaType.TEXT_PLAIN)
        String post(@QueryValue String q, @Body String body)
    }
}
