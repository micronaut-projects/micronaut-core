package io.micronaut.docs.server.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.uri.UriTemplate
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BookmarkControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test POJO binding"() {
        given:
        UriTemplate template = new UriTemplate("/api/bookmarks/list{?offset,max,sort,order}")
        String uri = template.expand([offset: 0, max: 10])

        when:
        HttpResponse response = client.toBlocking().exchange(HttpRequest.GET(uri))

        then:
        response.status() == HttpStatus.OK
    }
}
