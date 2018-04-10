package example.gateway

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.controllers.TokenRefreshRequest
import io.micronaut.security.controllers.UsernamePassword
import io.micronaut.security.token.AccessRefreshToken
import io.micronaut.security.token.DefaultAccessRefreshToken
import io.micronaut.security.token.validator.TokenValidator
import org.pac4j.core.profile.jwt.JwtClaims
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class OauthControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void '/oauth/access_token endpoint returns BAD request if required parameters not present'() {
        when:
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/oauth/access_token')
                .accept(MediaType.APPLICATION_FORM_URLENCODED_TYPE))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 400
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

    @Ignore
    void 'Users can generate a new token by requesting to /oauth/access_token with refresh token'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePassword('euler', 'password')), DefaultAccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()

        when:
        String accessToken = rsp.body.get().accessToken
        String refreshToken = rsp.body.get().refreshToken

        then:
        accessToken
        refreshToken

        when:
        HttpResponse<AccessRefreshToken> tokenRsp = client.toBlocking()
                .exchange(HttpRequest.create(HttpMethod.POST, '/oauth/access_token')
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .body(new TokenRefreshRequest("refresh_token", refreshToken)), DefaultAccessRefreshToken)

        then:
        tokenRsp.status.code == 200
        tokenRsp.body.isPresent()

        and: 'a new access token was generated'
        tokenRsp.body.get().accessToken
        tokenRsp.body.get().accessToken != accessToken
    }
}
