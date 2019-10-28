package io.micronaut.docs.client.filter;

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class BasicAuthFilterSpec extends Specification {

    void "test the filter is applied"() {
        ApplicationContext applicationContext = ApplicationContext.run(EmbeddedServer, ["spec.name": "BasicAuthFilterSpec"]).applicationContext
        BasicAuthClient client = applicationContext.getBean(BasicAuthClient)

        expect:
        client.message == "user:pass"
    }

    @Requires(property = "spec.name", value = "BasicAuthFilterSpec")
    @Controller("/message")
    static class BasicAuthController {

        @Get
        String message(io.micronaut.http.BasicAuth basicAuth) {
            "$basicAuth.username:$basicAuth.password"
        }
    }

}
