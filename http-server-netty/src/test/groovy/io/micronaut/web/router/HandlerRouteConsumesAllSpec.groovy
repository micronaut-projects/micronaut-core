package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.BodyRequestHandler
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.PathVariables
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.net.http.HttpClient
import java.net.http.HttpResponse as JdkResponse
import java.nio.charset.StandardCharsets

/**
 * A handler route that consumes every type reads its body as a route that consumes
 * {@code *}{@code /*}: without a content type with the reader of any type, and with a content type
 * with the reader of that type.
 */
class HandlerRouteConsumesAllSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HandlerRouteConsumesAllSpec'])

    @Shared
    HttpClient client = HttpClient.newHttpClient()

    @Unroll
    void "a body of #path with the content type #contentType is read"() {
        when:
        JdkResponse<String> response = post(path, contentType, body)

        then:
        response.statusCode() == 200
        response.body() == expected

        where:
        path            | contentType            | body                | expected
        '/all/string'   | null                   | 'hello'             | 'string hello'
        '/all/string'   | 'application/x-custom' | 'hello'             | 'string hello'
        '/all/string'   | 'text/plain'           | 'hello'             | 'string hello'
        '/all/pojo'     | null                   | '{"name":"Fred"}'   | 'pojo Fred'
        '/all/pojo'     | 'application/json'     | '{"name":"Fred"}'   | 'pojo Fred'
        '/all/custom'   | 'application/x-custom' | 'Fred'              | 'custom Fred'
        '/json/string'  | 'application/json'     | '"hello"'           | 'string "hello"'
    }

    private JdkResponse<String> post(String path, String contentType, String body) {
        def builder = java.net.http.HttpRequest.newBuilder(URI.create("${server.URL}${path}"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        if (contentType != null) {
            builder.header('Content-Type', contentType)
        }
        client.send(builder.build(), JdkResponse.BodyHandlers.ofString())
    }

    @Introspected
    static class Pojo {
        String name
    }

    static class Custom {
        final String name

        Custom(String name) {
            this.name = name
        }
    }

    @Singleton
    @Consumes('application/x-custom')
    @Requires(property = 'spec.name', value = 'HandlerRouteConsumesAllSpec')
    static class CustomReader implements MessageBodyReader<Custom> {
        @Override
        Custom read(Argument<Custom> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            return new Custom(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'HandlerRouteConsumesAllSpec')
    static class Routes {
        @Singleton
        HttpRoutes consumesAllRoutes() {
            return { HttpRouteBuilder routes ->
                routes.POST('/all/string', Argument.of(String), { HttpRequest<?> request, PathVariables pathVariables, String body ->
                    HttpResponse.ok("string ${body}".toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                } as BodyRequestHandler).consumesAll()
                routes.POST('/all/pojo', Argument.of(Pojo), { HttpRequest<?> request, PathVariables pathVariables, Pojo body ->
                    HttpResponse.ok("pojo ${body?.name}".toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                } as BodyRequestHandler).consumesAll()
                routes.POST('/all/custom', Argument.of(Custom), { HttpRequest<?> request, PathVariables pathVariables, Custom body ->
                    HttpResponse.ok("custom ${body?.name}".toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                } as BodyRequestHandler).consumesAll()
                routes.POST('/json/string', Argument.of(String), { HttpRequest<?> request, PathVariables pathVariables, String body ->
                    HttpResponse.ok("string ${body}".toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                } as BodyRequestHandler)
            } as HttpRoutes
        }
    }
}
