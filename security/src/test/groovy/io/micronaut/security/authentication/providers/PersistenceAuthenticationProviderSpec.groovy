package io.micronaut.security.authentication.providers

import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationFailure
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import spock.lang.Specification

class PersistenceAuthenticationProviderSpec extends Specification {

    def "if user is not found, AuthenticationFailed is returned"() {
        when:
        given:
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.empty()
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, null, null)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof AuthenticationFailed
        !(rsp as AuthenticationFailed).isAuthenticated()
        (rsp as AuthenticationFailed).authenticationFailure == AuthenticationFailure.USER_NOT_FOUND
    }

    def "if user is disabled, AuthenticationFailed is returned with authentication failure set to USER_DISABLED"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> false
            isAccountExpired() >> false
            isAccountLocked() >> false
            isPasswordExpired() >> false
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, null, null)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof AuthenticationFailed
        !(rsp as AuthenticationFailed).isAuthenticated()
        (rsp as AuthenticationFailed).authenticationFailure == AuthenticationFailure.USER_DISABLED
    }

    def "if user account is expired, AuthenticationFailed is returned with authentication failure set to ACCOUNT_EXPIRED"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> true
            isAccountExpired() >> true
            isAccountLocked() >> false
            isPasswordExpired() >> false
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, null, null)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof AuthenticationFailed
        !(rsp as AuthenticationFailed).isAuthenticated()
        (rsp as AuthenticationFailed).authenticationFailure == AuthenticationFailure.ACCOUNT_EXPIRED
    }

    def "if user account is locked, AuthenticationFailed is returned with authentication failure set to ACCOUNT_LOCKED"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> true
            isAccountExpired() >> false
            isAccountLocked() >> true
            isPasswordExpired() >> false
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, null, null)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof AuthenticationFailed
        !(rsp as AuthenticationFailed).isAuthenticated()
        (rsp as AuthenticationFailed).authenticationFailure == AuthenticationFailure.ACCOUNT_LOCKED
    }

    def "if user password is expired, AuthenticationFailed is returned with authentication failure set to PASSWORD_EXPIRED"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> true
            isAccountExpired() >> false
            isAccountLocked() >> false
            isPasswordExpired() >> true
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, null, null)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof AuthenticationFailed
        !(rsp as AuthenticationFailed).isAuthenticated()
        (rsp as AuthenticationFailed).authenticationFailure == AuthenticationFailure.PASSWORD_EXPIRED
    }

    def "if passwords do not match, AuthenticationFailed is returned with authentication failure set to CREDENTIALS_DO_NOT_MATCH"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> true
            isAccountExpired() >> false
            isAccountLocked() >> false
            isPasswordExpired() >> false
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        PasswordEncoder passwordEncoder = Stub(PasswordEncoder) {
            matches(_, _) >> false
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, passwordEncoder, null)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof AuthenticationFailed
        !(rsp as AuthenticationFailed).isAuthenticated()
        (rsp as AuthenticationFailed).authenticationFailure == AuthenticationFailure.CREDENTIALS_DO_NOT_MATCH
    }

    def "authoritiesFetcher is invoked once"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> true
            isAccountExpired() >> false
            isAccountLocked() >> false
            isPasswordExpired() >> false
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        PasswordEncoder passwordEncoder = Stub(PasswordEncoder) {
            matches(_, _) >> true
        }
        def authoritiesFetcher = Mock(AuthoritiesFetcher)
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, passwordEncoder, authoritiesFetcher)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        1 * authenticationProvider.authoritiesFetcher.findAuthoritiesByUsername('admin')
    }

    def "if authentication is succesful roles are populated in the response"() {
        when:
        given:
        UserState userState = Stub(UserState) {
            isEnabled() >> true
            isAccountExpired() >> false
            isAccountLocked() >> false
            isPasswordExpired() >> false
        }
        UserFetcher userFetcher = Stub(UserFetcher) {
            findByUsername(_) >> Optional.of(userState)
        }
        PasswordEncoder passwordEncoder = Stub(PasswordEncoder) {
            matches(_, _) >> true
        }
        def authoritiesFetcher = Stub(AuthoritiesFetcher) {
            findAuthoritiesByUsername(_) >> ['ROLE_USER', 'ROLE_ADMIN']
        }
        def authenticationProvider = new PersistenceAuthenticationProvider(userFetcher, passwordEncoder, authoritiesFetcher)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        AuthenticationResponse rsp = authenticationProvider.authenticate(creds)

        then:
        rsp instanceof UserDetails
        (rsp as UserDetails).roles == ['ROLE_USER', 'ROLE_ADMIN']
        (rsp as UserDetails).username == 'admin'
    }
}
