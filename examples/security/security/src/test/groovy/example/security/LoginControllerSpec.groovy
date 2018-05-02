package example.security

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.generator.claims.JwtClaims
import io.micronaut.security.token.jwt.render.AccessRefreshToken
import io.micronaut.security.token.validator.TokenValidator
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

    void '/login with valid credentials returns 200 and access token and refresh token'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('euler', 'password')), AccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()
        rsp.body.get().accessToken
        rsp.body.get().refreshToken
    }

    void '/login with valid credentials for a database user returns 200 and access token and refresh token'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('user', 'user')), AccessRefreshToken)

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
                .body(new UsernamePasswordCredentials('euler', 'bogus')))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    void 'access token contains expiration date'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('euler', 'password')), AccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()

        when:
        String accessToken = rsp.body.get().accessToken

        then:
        accessToken
        
        when:
        Optional<Authentication> authentication = tokenValidator.validateToken(accessToken)

        then:
        authentication.isPresent()

        and:
        authentication.get().getAttributes().get(JwtClaims.EXPIRATION_TIME)
    }

    void 'refresh token does not contain expiration date'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('euler', 'password')), AccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()

        when:
        String refreshToken = rsp.body.get().refreshToken

        then:
        refreshToken

        when:
        Optional<Authentication> authentication = getTokenValidator().validateToken(refreshToken)

        then:
        authentication.isPresent()

        and:
        !authentication.get().getAttributes().get(JwtClaims.EXPIRATION_TIME)
    }

    TokenValidator getTokenValidator() {
        embeddedServer.applicationContext.getBean(TokenValidator.class)
    }
}