package io.micronaut.docs.server.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.uri.UriTemplate
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MovieTicketControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test Bindable values POJO binding"() {
        given:
            UriTemplate template = new UriTemplate("/api/movie/ticket/terminator{?minPrice,maxPrice}")
            String uri = template.expand([minPrice: 5.0, maxPrice: 20.0])

        when:
            HttpResponse response = client.toBlocking().exchange(HttpRequest.GET(uri))

        then:
            response.status() == HttpStatus.OK
    }
}
