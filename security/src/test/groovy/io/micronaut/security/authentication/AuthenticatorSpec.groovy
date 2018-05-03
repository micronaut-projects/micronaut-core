package io.micronaut.security.authentication

import io.reactivex.Flowable
import spock.lang.Specification

class AuthenticatorSpec extends Specification {

    def "if no authentication providers return empty optional"() {
        given:
        Authenticator authenticator = new Authenticator()

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Flowable<AuthenticationResponse> rsp = Flowable.fromPublisher(authenticator.authenticate(creds))
        rsp.blockingFirst()

        then:
        thrown(NoSuchElementException)

    }

    def "if any authentication provider throws exception, continue with authentication"() {
        given:
        def authProviderExceptionRaiser = Stub(AuthenticationProvider) {
            authenticate(_) >> { Flowable.error( new Exception('Authentication provider raised exception') ) }
        }
        def authProviderOK = Stub(AuthenticationProvider) {
            authenticate(_) >> Flowable.just(new UserDetails('admin', []))
        }
        Authenticator authenticator = new Authenticator([authProviderExceptionRaiser, authProviderOK])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Flowable<AuthenticationResponse> rsp = authenticator.authenticate(creds)

        then:
        rsp.blockingFirst() instanceof UserDetails
    }

    def "if no authentication provider can authentication, the last error is sent back"() {
        given:
        def authProviderFailed = Stub(AuthenticationProvider) {
            authenticate(_) >> Flowable.just( new AuthenticationFailed() )
        }
        Authenticator authenticator = new Authenticator([authProviderFailed])

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        Flowable<AuthenticationResponse> rsp = Flowable.fromPublisher(authenticator.authenticate(creds))

        then:
        rsp.blockingFirst() instanceof AuthenticationFailed
    }
}
