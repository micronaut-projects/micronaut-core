package io.micronaut.security.token.reader

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class BearerTokenReaderSpec extends Specification {

    @Shared
    BearerTokenReaderConfiguration config = Stub(BearerTokenReaderConfiguration) {
        isEnabled() >> true
        getHeaderName() >> 'Authorization'
        getPrefix() >> 'Bearer'
    }

    @Subject
    @Shared
    BearerTokenReader bearerTokenReader = new BearerTokenReader(config)

    def extractTokenFromAuthorization() {
        expect:
        Optional.of('XXX') == bearerTokenReader.extractTokenFromAuthorization('Bearer XXX')

        and:
        Optional.empty() == bearerTokenReader.extractTokenFromAuthorization('BearerXXX')

        and:
        Optional.empty() == bearerTokenReader.extractTokenFromAuthorization('XXX')
    }

    def "if authorization header not present returns null"() {
        expect:
        Optional.empty() == bearerTokenReader.findTokenAtAuthorizationHeader(Optional.empty())
    }

    def "findTokenAtAuthorizationHeader parses header correctly"() {
        expect:
        Optional.of('XXX') == bearerTokenReader.findTokenAtAuthorizationHeader(Optional.of('Bearer XXX'))
    }
}
