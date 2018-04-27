package io.micronaut.security.token.reader

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.security.token.reader.bearer.BearerTokenReader
import io.micronaut.security.token.reader.bearer.BearerTokenReaderConfiguration
import spock.lang.Shared
import spock.lang.Specification

class BearerTokenReaderSpec extends Specification {

    @Shared
    BearerTokenReaderConfiguration config = Stub(BearerTokenReaderConfiguration) {
        isEnabled() >> true
        getHeaderName() >> 'Authorization'
        getPrefix() >> 'Bearer'
    }

    @Shared
    BearerTokenReader bearerTokenReader = new BearerTokenReader(config)

    def extractTokenFromAuthorization() {
        expect:
        bearerTokenReader.extractTokenFromAuthorization('Bearer XXX').get() == 'XXX'

        and:
        !bearerTokenReader.extractTokenFromAuthorization('BearerXXX').isPresent()

        and:
        !bearerTokenReader.extractTokenFromAuthorization('XXX').isPresent()
    }

    def "if authorization header not present returns empty"() {
        given:
        def request = HttpRequest.create(HttpMethod.GET, '/')

        expect:
        !bearerTokenReader.findToken(request).isPresent()
    }

    def "findTokenAtAuthorizationHeader parses header correctly"() {
        given:
        def request = HttpRequest.create(HttpMethod.GET, '/').header('Authorization', 'Bearer XXX')

        expect:
        bearerTokenReader.findToken(request).get() == 'XXX'
    }
}
