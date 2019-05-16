package io.micronaut.http.server.hostresolver

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpHeaderHostResolverSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run()

    void "HttpHeaderHostResolver can resolve host from HTTP Headers"() {
        given:
        HttpHeaderHostResolver resolver = applicationContext.getBean(HttpHeaderHostResolver)
        def headers = Stub(HttpHeaders) {
            get("Host") >> 'micronaut.io'
            get("X-Forwarded-For") >> 'https'
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> headers
        }

        expect:
        resolver.resolve(request) == 'https://micronaut.io'
    }

    void "HttpHeaderHostResolver resolve host embededded server if HTTP Headers not set"() {
        given:
        HttpHeaderHostResolver resolver = applicationContext.getBean(HttpHeaderHostResolver)
        def headers = Stub(HttpHeaders) {
            get("Host") >> null
            get("X-Forwarded-For") >> null
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> headers
            getUri() >> URI.create("https://grails.org")
        }

        expect:
        resolver.resolve(request) == 'https://grails.org'
    }

}
