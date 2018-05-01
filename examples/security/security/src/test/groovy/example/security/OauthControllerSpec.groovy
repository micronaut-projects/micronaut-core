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
import io.micronaut.security.endpoints.TokenRefreshRequest
import io.micronaut.security.token.jwt.generator.claims.JwtClaims
import io.micronaut.security.token.jwt.render.AccessRefreshToken
import io.micronaut.security.token.validator.TokenValidator
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

    @Ignore
    void 'Users can generate a new token by requesting to /oauth/access_token with refresh token'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('euler', 'password')), AccessRefreshToken)

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
                .body(new TokenRefreshRequest("refresh_token", refreshToken)), AccessRefreshToken)

        then:
        tokenRsp.status.code == 200
        tokenRsp.body.isPresent()

        and: 'a new access token was generated'
        tokenRsp.body.get().accessToken
        tokenRsp.body.get().accessToken != accessToken

        when:
        TokenValidator tokenValidator = embeddedServer.applicationContext.getBean(TokenValidator)
        Optional<Authentication> authentication = tokenValidator.validateToken(accessToken)

        then:
        authentication.isPresent()

        and:
        authentication.get().getAttributes().get(JwtClaims.EXPIRATION_TIME)
    }
}