package example.gateway

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.render.AccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class BooksControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

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

    @Ignore
    void 'a user granted ROLE_GROOVY is able to access /books/groovy'() {
        when:
        HttpResponse<AccessRefreshToken> rsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.POST, '/login')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .body(new UsernamePasswordCredentials('newton', 'password')), AccessRefreshToken)

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
        booksRsp.body.get().size() == TestBooksClientFallback.GROOVY_BOOKS.size()

        when: 'trying to access a resource for which the user does not have the required role'
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/grails')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),List)

        then: 'app responds FORBIDDEN'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 403
    }

    @Ignore
    void 'a user granted ROLE_GRAILS is able to access /books/grails'() {
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
        HttpResponse<List> booksRsp = client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/grails')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"), List)

        then:
        booksRsp.status.code == 200
        booksRsp.body.isPresent()
        booksRsp.body.get().size() == TestBooksClientFallback.GRAILS_BOOKS.size()

        when: 'trying to access a resource for which the user does not have the required role'
        client.toBlocking().exchange(HttpRequest.create(HttpMethod.GET, '/books/groovy')
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),List)

        then: 'app responds FORBIDDEN'
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 403
    }


}