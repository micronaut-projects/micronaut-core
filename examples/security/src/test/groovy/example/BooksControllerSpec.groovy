package example

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

class BooksControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void '/ allows anonymous access'() {
        when:
        HttpResponse rsp = client.toBlocking().exchange("/")

        then:
        rsp.status.code == 200
    }

    void 'urls not mapped in InterceptUrlMap return UNAUTHORIZED if they attempted to be accessed'() {
        when:
        client.toBlocking().exchange("/notInInterceptUrlMap")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    void '/health endpoint, which is configured with sensitive false, is allowed anonymously'() {
        when:
        HttpResponse rsp = client.toBlocking().exchange("/health")

        then:
        rsp.status.code == 200
    }

    void 'attempt to access /beans without authenticating and server responds UNAUTHORIZED'() {
        when: 'attempt to access /beans without authenticating'
        client.toBlocking().exchange("/beans")

        then: 'server responds UNAUTHORIZED'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    void 'attempt to access /book/groovy without authenticating and server responds UNAUTHORIZED'() {
        when: 'attempt to access /book/groovy without authenticating'
        client.toBlocking().exchange("/books/groovy")

        then: 'server responds UNAUTHORIZED'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    void 'attempt to access /book/grails without authenticating and server responds UNAUTHORIZED'() {
        when: 'attempt to access /book/grails without authenticating'
        client.toBlocking().exchange("/books/grails")

        then: 'server responds UNAUTHORIZED'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

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

    void 'a user granted ROLE_GROOVY is able to access /books/groovy'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePassword('newton', 'password')), DefaultAccessRefreshToken)

        then:
        rsp.status.code == 200
        rsp.body.isPresent()

        when:
        String accessToken = rsp.body.get().accessToken

        then:
        accessToken

        when:
        HttpResponse<List> booksRsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/groovy')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"), List)

        then:
        booksRsp.status.code == 200
        booksRsp.body.isPresent()
        booksRsp.body.get().size() == BooksRepositoryService.GROOVY_BOOKS.size()

        when: 'trying to access a resource for which the user does not have the required role'
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/grails')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),List)

        then: 'app responds FORBIDDEN'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 403
    }

    void 'a user granted ROLE_GRAILS is able to access /books/grails'() {
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
        HttpResponse<List> booksRsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/grails')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"), List)

        then:
        booksRsp.status.code == 200
        booksRsp.body.isPresent()
        booksRsp.body.get().size() == BooksRepositoryService.GRAILS_BOOKS.size()

        when: 'trying to access a resource for which the user does not have the required role'
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/groovy')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),List)

        then: 'app responds FORBIDDEN'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 403
    }

    void '/token endpoint returns BAD request if required parameters not present'() {
        when:
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/token')
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

    void 'Users can generate a new token by requesting to /token with refresh token'() {
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
                .exchange(HttpRequest.create(HttpMethod.POST, '/token')
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