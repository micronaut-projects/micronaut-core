package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class FormLocatorRoutesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ["spec.name": "FormLocatorRoutesSpec"])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the locator and the located route read the form"() {
        given:
        BlockingHttpClient http = client.toBlocking()

        expect:
        http.retrieve(HttpRequest.POST("/carts/items", "customer=ann&item=tea")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) == "ann added tea"
        // not a form: located with the empty form
        http.retrieve(HttpRequest.GET("/carts")) == "guest"
    }
}
