package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class BodyArgumentConversionSpec extends Specification {

    static final List<Book> READ_BOOKS = [new Book(title: 'Read')]

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'BodyArgumentConversionSpec'])

    @Shared
    @AutoCleanup
    EmbeddedServer server = ctx.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient httpClient = ctx.createBean(HttpClient, server.URI)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    void "a JSON body is bound to List<Pojo>"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/list', '[{"title":"A"},{"title":"B"}]'), String) == 'Book:A,Book:B'
    }

    void "a JSON body is bound to Set<Pojo>"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/set', '[{"title":"A"},{"title":"A"},{"title":"B"}]'), String) == 'Book:A,Book:B'
    }

    void "a JSON body is bound to Map<String, Pojo>"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/map', '{"x":{"title":"A"},"y":{"title":"B"}}'), String) == 'x=Book:A,y=Book:B'
    }

    void "a JSON body is bound to List<Integer>"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/integers', '[1,2,3]'), String) == 'Integer:1,Integer:2,Integer:3'
    }

    void "a JSON body is bound to List<Long>"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/longs', '[1,2,3]'), String) == 'Long:1,Long:2,Long:3'
    }

    void "a body read for the argument type is passed to the route without being copied"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/identity', 'ignored').contentType('application/x-books'), String) == 'true'
    }

    void "a value bound by a non-body binder is still converted to the argument type"() {
        expect:
        client.retrieve(HttpRequest.POST('/body-conversion/raw-strings', '{}'), String) == 'Integer:1,Integer:2,Integer:3'
    }

    @Introspected
    static class Book {
        String title

        @Override
        boolean equals(Object o) {
            o instanceof Book && o.title == title
        }

        @Override
        int hashCode() {
            title.hashCode()
        }
    }

    @Controller('/body-conversion')
    @Requires(property = 'spec.name', value = 'BodyArgumentConversionSpec')
    static class BodyConversionController {

        @Post('/list')
        String list(@Body List<Book> books) {
            books.collect { it.getClass().simpleName + ':' + it.title }.join(',')
        }

        @Post('/set')
        String set(@Body Set<Book> books) {
            books.collect { it.getClass().simpleName + ':' + it.title }.sort().join(',')
        }

        @Post('/map')
        String map(@Body Map<String, Book> books) {
            books.collect { k, v -> k + '=' + v.getClass().simpleName + ':' + v.title }.sort().join(',')
        }

        @Post('/integers')
        String integers(@Body List<Integer> values) {
            values.collect { it.getClass().simpleName + ':' + it }.join(',')
        }

        @Post('/longs')
        String longs(@Body List<Long> values) {
            values.collect { it.getClass().simpleName + ':' + it }.join(',')
        }

        @Post(value = '/identity', consumes = 'application/x-books')
        String identity(@Body List<Book> books) {
            String.valueOf(books.is(READ_BOOKS))
        }

        @Post('/raw-strings')
        String rawStrings(@RawStrings List<Integer> values) {
            values.collect { it.getClass().simpleName + ':' + it }.join(',')
        }
    }

    @Singleton
    @Consumes('application/x-books')
    @Requires(property = 'spec.name', value = 'BodyArgumentConversionSpec')
    static class BooksReader implements MessageBodyReader<List<Book>> {
        @Override
        List<Book> read(Argument<List<Book>> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            inputStream.readAllBytes()
            return READ_BOOKS
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'BodyArgumentConversionSpec')
    static class RawStringsBinder implements AnnotatedRequestArgumentBinder<RawStrings, Object> {
        @Override
        Class<RawStrings> getAnnotationType() {
            RawStrings
        }

        @Override
        BindingResult<Object> bind(ArgumentConversionContext<Object> context, HttpRequest<?> source) {
            // deliberately ignores the type arguments of the bound argument
            List<String> strings = ['1', '2', '3']
            return () -> Optional.of((Object) strings)
        }
    }
}

@Bindable
@Retention(RetentionPolicy.RUNTIME)
@interface RawStrings {
}
