package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MalformedUriSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test malformed URI exceptions"() {
        when:
        def result = new URL("$embeddedServer.URL/malformed/[]").text

        then:
        result == 'Exception: Illegal character in path at index 11: /malformed/[]'
    }

    @Controller('/malformed')
    static class SomeController {
        @Get(uri="/{some}", produces = MediaType.TEXT_PLAIN)
        String some(String some) throws Exception{
            return some
        }

        @Error(exception = URISyntaxException.class, global = true)
        HttpResponse<String> exception(HttpRequest request, URISyntaxException e) {
            return HttpResponse.<String>ok()
                    .body("Exception: " + e.getMessage())
        }
    }
}
