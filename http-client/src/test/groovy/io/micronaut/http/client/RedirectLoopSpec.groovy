package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class RedirectLoopSpec extends Specification {
    void "netty client enforces configurable max redirect depth"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': 'RedirectLoopSpec',
                'micronaut.http.client.max-redirects': 3,
        ])
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, server.getURL())

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/redirect/loop').header('X-Redirect-Count', '0'))

        then:
        HttpClientException e = thrown()
        e.message == 'Maximum number of redirects exceeded at redirect count: 4'

        cleanup:
        client.close()
        server.close()
        context.close()
    }

    @Requires(property = 'spec.name', value = 'RedirectLoopSpec')
    @Controller('/redirect')
    static class RedirectLoopController {
        @Get('/loop')
        HttpResponse<?> loop(HttpRequest<?> request) {
            int redirectCount = Integer.parseInt(request.headers.get('X-Redirect-Count') ?: '0')
            HttpResponse.redirect(java.net.URI.create('/redirect/loop'))
                    .header('X-Redirect-Count', Integer.toString(redirectCount + 1))
        }
    }
}
