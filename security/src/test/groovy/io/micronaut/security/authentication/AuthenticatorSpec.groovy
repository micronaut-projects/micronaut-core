package io.micronaut.security.authentication

import spock.lang.Specification

class AuthenticatorSpec extends Specification {

    def "if no authentication providers return empty optional"() {
        given:
        Authenticator authenticator = new Authenticator()

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Optional<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        !rsp.isPresent()
    }

    def "if any authentication provider throws exception, continue with authentication"() {
        given:
        def authProviderExceptionRaiser = Stub(AuthenticationProvider) {
            authenticate(_) >> { throw new Exception('Authentication provider raised exception') }
        }
        def authProviderOK = Stub(AuthenticationProvider) {
            authenticate(_) >> new UserDetails('admin', [])
        }
        Authenticator authenticator = new Authenticator([authProviderExceptionRaiser, authProviderOK])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Optional<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        rsp.isPresent()
        rsp.get() instanceof UserDetails
    }

    def "if no authentication provider can authentication, the last error is sent back"() {
        given:
        def authProviderFailed = Stub(AuthenticationProvider) {
            authenticate(_) >> new AuthenticationFailed()
        }
        Authenticator authenticator = new Authenticator([authProviderFailed])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Optional<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        rsp.get() instanceof AuthenticationFailed
    }
}
