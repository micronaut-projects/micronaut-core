package io.micronaut.http.server.netty.redirect

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import java.security.Principal

class FollowRedirectSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ["spec.name": "FollowRedirectSpec"])
    @Shared @AutoCleanup RxHttpClient httpClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test followed redirect error"() {
        when:
        httpClient.exchange(HttpRequest.POST("/call", "test")).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message.contains("redirect error")
    }

    @Requires(property = "spec.name", value = "FollowRedirectSpec")
    @Controller("/")
    static class RedirectController {

        @Inject @Client("/") RxHttpClient client

        @Post("/redirect")
        HttpResponse redirect(@Body String body) {
            HttpResponse.redirect(URI.create('/error'))
        }

        @Post("/call")
        Single<HttpResponse> call(@Body String body) {
            client.exchange(HttpRequest.POST("/redirect", body)).firstOrError()
        }

        @Get("/error") //unsatisfied argument exception
        HttpResponse error(Principal principal) {
            return HttpResponse.ok()
        }
    }
}
