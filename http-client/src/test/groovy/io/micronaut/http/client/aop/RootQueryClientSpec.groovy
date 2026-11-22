package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.annotation.Nullable
import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14444')
class RootQueryClientSpec extends Specification {

    void "a declarative client aimed at a root URL sends a path with the query"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'RootQueryClientSpec',
        ])
        // no path and no trailing slash: http://host:port
        String rootUrl = server.URL.toString()
        assert server.URI.rawPath.empty
        server.applicationContext.environment.addPropertySource(PropertySource.of(
                'root-url',
                ['root.url': rootUrl]
        ))
        RootClient client = server.applicationContext.getBean(RootClient)

        expect:
        client.withTemplate('x') == 'q=x'
        client.withoutTemplate('x') == 'q=x'

        cleanup:
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'RootQueryClientSpec')
    @Controller
    static class RootController {
        @Get
        String list(@Nullable @QueryValue String q) {
            return "q=$q"
        }
    }

    @Requires(property = 'spec.name', value = 'RootQueryClientSpec')
    @Client('${root.url}')
    static interface RootClient {
        @Get('{?q}')
        String withTemplate(@QueryValue String q)

        @Get
        String withoutTemplate(@QueryValue String q)
    }
}
