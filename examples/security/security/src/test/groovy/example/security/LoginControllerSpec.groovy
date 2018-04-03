package example.security

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.UsernamePassword
import io.micronaut.security.jwt.AccessRefreshToken
import io.micronaut.security.jwt.DefaultAccessRefreshToken
import io.micronaut.security.jwt.TokenRefreshRequest
import io.micronaut.security.jwt.TokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class LoginControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void 'attempt to access /login without supplying credentials server responds BAD REQUEST'() {
        when:
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 400
    }

    void '/login without valid credentials returns 200 and access token and refresh token'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePassword('euler', 'password')),DefaultAccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()
        rsp.body.get().accessToken
        rsp.body.get().refreshToken
    }

    void '/login with invalid credentials returns UNAUTHORIZED'() {
        when:
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePassword('euler', 'bogus')))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    void 'access token contains expiration date'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePassword('euler', 'password')), DefaultAccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()

        when:
        String accessToken = rsp.body.get().accessToken

        then:
        accessToken

        when:
        TokenValidator tokenValidator = embeddedServer.applicationContext.getBean(TokenValidator)
        Map<String, Object> claims = tokenValidator.validateTokenAndGetClaims(accessToken)

        then:
        claims

        and:
        claims.get(JwtClaims.EXPIRATION_TIME)
    }

    void 'refresh token does not contain expiration date'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePassword('euler', 'password')), DefaultAccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()

        when:
        String refreshToken = rsp.body.get().refreshToken

        then:
        refreshToken

        when:
        TokenValidator tokenValidator = embeddedServer.applicationContext.getBean(TokenValidator)
        Map<String, Object> claims = tokenValidator.validateTokenAndGetClaims(refreshToken)

        then:
        claims

        and:
        !claims.get(JwtClaims.EXPIRATION_TIME)
    }
}