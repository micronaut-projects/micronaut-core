package io.micronaut.security.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.handlers.LoginHandler
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

class LoginControllerPathConfigurableSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'loginpathconfigurable',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
            'micronaut.security.endpoints.login.path': '/auth',
    ], 'test')

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


    void "LoginController is not accessible at /login but at /auth"() {
        given:
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials('user', 'password')

        when:
        client.toBlocking().exchange(HttpRequest.POST('/login', creds))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        client.toBlocking().exchange(HttpRequest.POST('/auth', creds))

        then:
        noExceptionThrown()
    }

    @Requires(property = 'spec.name', value = 'loginpathconfigurable')
    @Singleton
    static class CustomLoginHandler implements LoginHandler {

        @Override
        HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
            HttpResponse.ok()
        }

        @Override
        HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
            HttpResponse.unauthorized()
        }
    }

    @Requires(property = 'spec.name', value = 'loginpathconfigurable')
    @Singleton
    static class CustomAuthenticationProvider implements AuthenticationProvider {

        @Override
        Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            return Flowable.just(new UserDetails("user", []))
        }
    }
}
