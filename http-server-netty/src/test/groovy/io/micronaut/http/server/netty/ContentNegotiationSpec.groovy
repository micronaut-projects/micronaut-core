package io.micronaut.http.server.netty

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject
import static io.micronaut.http.server.netty.ContentNegotiationSpec.NegotiatingController.*

@MicronautTest
class ContentNegotiationSpec extends Specification {

    @Inject
    @Client("/")
    RxHttpClient client


    @Unroll
    void "test ACCEPT header content negotiation #header"() {
        expect:
        client.retrieve(HttpRequest.GET("/negotiate").accept(header as MediaType[]), String)
                .blockingFirst() == response

        where:
        header                                                                            | response
        [MediaType.APPLICATION_GRAPHQL_TYPE]                                              | JSON // should default to the all handler
        [new MediaType("application/json;q=0.5"), new MediaType("application/xml;q=0.9")] | XML
        [MediaType.APPLICATION_JSON_TYPE]                                                 | JSON
        [MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_XML_TYPE]                 | JSON
        [MediaType.APPLICATION_XML_TYPE, MediaType.APPLICATION_JSON_TYPE]                 | XML
        [MediaType.APPLICATION_XML_TYPE]                                                  | XML
        [MediaType.TEXT_PLAIN_TYPE]                                                       | TEXT
        [MediaType.ALL_TYPE]                                                              | JSON

    }

    void "test send unacceptable type"() {
        when: "An unacceptable type is sent"
        client.retrieve(HttpRequest.GET("/negotiate/other")
                .accept(MediaType.APPLICATION_GRAPHQL), Argument.STRING, JsonError.TYPE)
                .blockingFirst()

        then: "An exception is thrown that states the acceptable types"
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.status() == HttpStatus.NOT_ACCEPTABLE
        response.body().toString().contains("Specified Accept Types [application/graphql] not supported. Supported types: [text/plain]")
    }

    @Controller("/negotiate")
    static class NegotiatingController {

        public static final String XML = "<hello>world</hello>"
        public static final String JSON = '{"hello":"world"}'
        public static final String TEXT = 'Hello World'

        @Get("/")
        @Produces(MediaType.APPLICATION_XML)
        String xml() {
            return XML
        }

        @Get("/")
        @Produces([MediaType.APPLICATION_JSON, MediaType.ALL])
        String json() {
            return JSON
        }

        @Get("/")
        @Produces(MediaType.TEXT_PLAIN)
        String text() {
            return TEXT
        }

        @Get("/other")
        @Produces(MediaType.TEXT_PLAIN)
        String other() {
            return TEXT
        }
    }
}
