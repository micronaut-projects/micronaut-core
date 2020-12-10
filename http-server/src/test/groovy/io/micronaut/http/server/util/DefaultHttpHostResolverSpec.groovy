package io.micronaut.http.server.util

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import spock.lang.Specification

class DefaultHttpHostResolverSpec extends Specification {

    void "test host resolver with no headers and no embedded server"() {
        ApplicationContext applicationContext = ApplicationContext.run()
        HttpHostResolver hostResolver = applicationContext.getBean(HttpHostResolver)
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([:])
            getUri() >> new URI("/")
        }

        expect:
        hostResolver.resolve(request) == "http://localhost"
        hostResolver.resolve(null) == "http://localhost"
    }
}
