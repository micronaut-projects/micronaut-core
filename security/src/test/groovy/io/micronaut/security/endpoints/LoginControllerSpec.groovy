package io.micronaut.security.endpoints

import io.micronaut.security.authentication.Authenticator
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator
import io.micronaut.security.token.generator.TokenConfiguration
import spock.lang.Specification

class LoginControllerSpec extends Specification {

    def "if authenticator returns empty, return empty"() {
        given:
        def accessRefreshTokenGenerator = Mock(AccessRefreshTokenGenerator)
        def tokenConfiguration = Mock(TokenConfiguration)
        def authenticator = Stub(Authenticator) {
            authenticate(_) >> Optional.empty()
        }
        LoginController loginController = new LoginController(accessRefreshTokenGenerator, tokenConfiguration, authenticator)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        def rsp = loginController.authenticate(creds)

        then:
        !rsp.isPresent()
    }

    def "if authenticator returns user details authenticate, it is returned"() {
        given:
        def accessRefreshTokenGenerator = Mock(AccessRefreshTokenGenerator)
        def tokenConfiguration = Mock(TokenConfiguration)
        def authenticator = Stub(Authenticator) {
            authenticate(_) >> Optional.of(new UserDetails('admin', ['ROLE_USER']))
        }
        LoginController loginController = new LoginController(accessRefreshTokenGenerator, tokenConfiguration, authenticator)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        def rsp = loginController.authenticate(creds)

        then:
        rsp.isPresent()
        rsp.get().username == 'admin'
        rsp.get().roles == ['ROLE_USER']
    }
}
