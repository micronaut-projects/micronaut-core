package io.micronaut.security.handlers

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RedirectRejectionHandlerSpec extends Specification {

    @Shared
    String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': RedirectRejectionHandlerSpec.simpleName,
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "UnauthorizedRejectionUriProvider is used for 401"() {
        when: 'accessing a secured page without authenticating'
        HttpRequest request = HttpRequest.GET("/secured").header("Accept", accept)
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then: 'user is redirected to the url provided by CustomUnauthorizedRejectionUriProvider'
        rsp.status() == HttpStatus.OK
        rsp.body() == 'login'
    }

    def "ForbiddenRejectionUriProvider is used for 401"() {
        when: 'accessing a secured page authenticating'
        HttpRequest request = HttpRequest.GET("/secured")
                .header("Accept", accept)
                .basicAuth("sherlock", "elementary")
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then: 'no redirection takes place'
        rsp.status() == HttpStatus.OK
        rsp.body() == 'secured'

        when: 'accessing a restricted page without authentication but without required roles'
        request = HttpRequest.GET("/admin")
                .header("Accept", accept)
                .basicAuth("sherlock", "elementary")
        rsp = client.toBlocking().exchange(request, String)

        then: 'user is redirected to the url provided by ForbiddenRejectionUriProvider'
        rsp.status() == HttpStatus.OK
        rsp.body() == 'forbidden'
    }
}
