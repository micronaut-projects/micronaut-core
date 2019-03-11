package io.micronaut.security.authentication

import spock.lang.Specification

class AuthenticationFailedSpec extends Specification {

    def "createMessage generates a Title Case string"() {
        expect:
        new AuthenticationFailed()
                .createMessage(AuthenticationFailureReason.USER_NOT_FOUND) == 'User Not Found'
    }

    def "getReason() is accesible"() {
        expect:
        new AuthenticationFailed().getReason() == AuthenticationFailureReason.UNKNOWN
    }

    def "constructor generates a Title Case string"() {
        expect:
        new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND)
                .getMessage().get() == 'User Not Found'
    }
}
