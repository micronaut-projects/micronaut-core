package io.micronaut.web.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.body.AsyncRequestBody
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.client.HttpClient
import io.micronaut.http.codec.CodecException
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.builder.AsyncBodyRequestHandler
import io.micronaut.web.router.builder.BodyRequestHandler
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.PathVariables
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A message body reader sees the annotations of the body type given to a handler route.
 */
class HandlerRouteBodyAnnotationsSpec extends Specification {

    static final String ENTITY = 'test.EntityAnnotation'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HandlerRouteBodyAnnotationsSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the reader sees the annotations of the body type of a #kind handler"() {
        given:
        TaggedReader reader = server.applicationContext.getBean(TaggedReader)
        reader.readable.clear()

        expect: 'the reader reads the body with the annotations of the body type'
        post(path, 'hello') == "hello $tag".toString()

        and: 'a route with a body type selects its reader with them; an asynchronous handler gives the type when it reads, and the registry selects the reader by type'
        !routeBodyType || reader.readable.contains(tag)

        where:
        kind       | path         | tag        | routeBodyType
        'sync'     | '/b/sync'    | 'sync'     | true
        'async'    | '/b/async'   | 'async'    | false
        'nullable' | '/b/nullable'| 'nullable' | true
    }

    void "a nullable body handler without a body receives null"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST('/b/nullable', null)) == 'null'
    }

    private String post(String path, String body) {
        client.toBlocking().retrieve(HttpRequest.POST(path, body).contentType(MediaType.TEXT_PLAIN_TYPE))
    }

    static Argument<Tagged> tagged(String tag) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(ENTITY, [value: tag])
        return Argument.of(Tagged, 'entity', metadata)
    }

    private static HttpResponse<String> text(Object value) {
        HttpResponse.ok(String.valueOf(value)).contentType(MediaType.TEXT_PLAIN_TYPE)
    }

    static class Tagged {
        final String text

        Tagged(String text) {
            this.text = text
        }

        @Override
        String toString() {
            text
        }
    }

    @Singleton
    @Consumes(MediaType.TEXT_PLAIN)
    @Requires(property = 'spec.name', value = 'HandlerRouteBodyAnnotationsSpec')
    static class TaggedReader implements MessageBodyReader<Tagged> {
        final List<String> readable = Collections.synchronizedList([])

        @Override
        boolean isReadable(Argument<Tagged> type, MediaType mediaType) {
            String tag = tag(type.annotationMetadata)
            readable.add(tag)
            return true
        }

        @Override
        Tagged read(Argument<Tagged> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            String tag = tag(type.annotationMetadata)
            return new Tagged(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8) + ' ' + tag)
        }

        private static String tag(AnnotationMetadata metadata) {
            if (!metadata.hasAnnotation(io.micronaut.http.annotation.Body)) {
                throw new AssertionError('The body argument lost @Body')
            }
            return metadata.stringValue(ENTITY).orElseThrow { new AssertionError('The body argument lost ' + ENTITY) }
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'HandlerRouteBodyAnnotationsSpec')
    static class Routes {
        @Singleton
        HttpRoutes bodyRoutes() {
            return { HttpRouteBuilder routes ->
                routes.POST('/b/sync', tagged('sync'), { HttpRequest<?> request, PathVariables variables, Tagged body ->
                    text(body)
                } as BodyRequestHandler<Tagged>).consumes(MediaType.TEXT_PLAIN_TYPE)
                routes.asyncPOST('/b/async', { HttpRequest<?> request, PathVariables variables, AsyncRequestBody asyncBody ->
                    asyncBody.body(tagged('async')).thenApply { Tagged body -> text(body) }
                } as AsyncBodyRequestHandler).consumes(MediaType.TEXT_PLAIN_TYPE)
                routes.POST('/b/nullable', HttpRouteBuilder.nullableBody(tagged('nullable')), { HttpRequest<?> request, PathVariables variables, Tagged body ->
                    text(body)
                } as BodyRequestHandler<Tagged>).consumes(MediaType.TEXT_PLAIN_TYPE)
            } as HttpRoutes
        }
    }
}
