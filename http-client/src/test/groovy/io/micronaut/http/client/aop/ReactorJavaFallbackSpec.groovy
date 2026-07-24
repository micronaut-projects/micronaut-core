package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactorJavaFallbackSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ReactorJavaFallbackSpec'])

    void "test that fallbacks are called for RxJava responses"() {
        given:
        BookClient client = embeddedServer.applicationContext.getBean(BookClient)

        when:
        Book book = Mono.from(client.get(99)).block()
        List<Book> books = Mono.from(client.list()).block()

        List<Book> stream = Flux.from(client.stream()).collectList().block()

        then:
        book.title == "Fallback Book"
        books.size() == 0
        stream.size() == 1
        stream.first().title == "Fallback Book"

        when:
        book = Mono.from(client.save("The Stand")).block()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = Mono.from(client.get(1)).block()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = Mono.from(client.update(1, "The Shining")).block()

        then:
        book != null
        book.title == "Fallback Book"
        book.id == null

        when:
        book = Mono.from(client.delete(1)).block()

        then:
        book == null

        when:
        book = Mono.from(client.get(1)).block()

        then:
        book.title == "Fallback Book"
    }

    void "test fallback excludes and includes are honored for reactive responses"() {
        given:
        ConditionalBookClient client = embeddedServer.applicationContext.getBean(ConditionalBookClient)

        when:
        Mono.from(client.excluded(99)).block()

        then:
        thrown(HttpClientResponseException)

        when:
        Book included = Mono.from(client.included(99)).block()

        then:
        included.title == "Fallback Book"
    }

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Client('/rxjava/fallback/books')
    @Recoverable
    static interface BookClient extends BookApi {
    }

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Client('/rxjava/fallback/books')
    @Recoverable
    static interface ConditionalBookClient extends ConditionalBookApi {
    }

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Fallback
    static class BookFallback implements BookApi, ConditionalBookApi {

        @Override
        @SingleResult
        Publisher<Book> get(Long id) {
            return Mono.just(new Book(title: "Fallback Book"))
        }

        @Override
        @SingleResult
        Publisher<List<Book>> list() {
            return Mono.just([])
        }

        @Override
        Publisher<Book> stream() {
            return Flux.fromArray(new Book(title: "Fallback Book"))
        }

        @Override
        @SingleResult
        Publisher<Book> delete(Long id) {
            return Mono.empty()
        }

        @Override
        @SingleResult
        Publisher<Book> save(String title) {
            return Mono.just(new Book(title: "Fallback Book"))
        }

        @Override
        @SingleResult
        Publisher<Book> update(Long id, String title) {
            return Mono.just(new Book(title: "Fallback Book"))
        }

        @Override
        @SingleResult
        @Fallback(excludes = [HttpClientResponseException])
        Publisher<Book> excluded(Long id) {
            return Mono.just(new Book(title: "Fallback Book"))
        }

        @Override
        @SingleResult
        @Fallback(includes = [HttpClientResponseException])
        Publisher<Book> included(Long id) {
            return Mono.just(new Book(title: "Fallback Book"))
        }
    }

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Controller("/rxjava/fallback/books")
    static class BookController implements BookApi, ConditionalBookApi {

        @Override
        @SingleResult
        Publisher<Book> get(Long id) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        @SingleResult
        Publisher<List<Book>> list() {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        Publisher<Book> stream() {
            Flux.error(new RuntimeException("bad"))
        }

        @Override
        @SingleResult
        Publisher<Book> delete(Long id) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        @SingleResult
        Publisher<Book> save(String title) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        @SingleResult
        Publisher<Book> update(Long id, String title) {
            Mono.error(new RuntimeException("bad"))
        }

        @Override
        @SingleResult
        Publisher<Book> excluded(Long id) {
            Mono.error(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        }

        @Override
        @SingleResult
        Publisher<Book> included(Long id) {
            Mono.error(new HttpClientResponseException("Not Found", HttpResponse.notFound()))
        }
    }

    static interface BookApi {

        @Get("/{id}")
        @SingleResult
        Publisher<Book> get(Long id)

        @Get
        @SingleResult
        Publisher<List<Book>> list()

        @Get('/stream')
        Publisher<Book> stream()

        @Delete("/{id}")
        @SingleResult
        Publisher<Book> delete(Long id)

        @Post
        @SingleResult
        Publisher<Book> save(String title)

        @Patch("/{id}")
        @SingleResult
        Publisher<Book> update(Long id, String title)
    }

    static interface ConditionalBookApi {

        @Get("/excluded/{id}")
        @SingleResult
        Publisher<Book> excluded(Long id)

        @Get("/included/{id}")
        @SingleResult
        Publisher<Book> included(Long id)
    }

    static class Book {
        Long id
        String title
    }
}
