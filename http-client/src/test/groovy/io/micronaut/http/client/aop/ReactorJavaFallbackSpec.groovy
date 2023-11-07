package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.*
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Fallback
import io.micronaut.retry.annotation.Recoverable
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification
import io.micronaut.core.async.annotation.SingleResult

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactorJavaFallbackSpec extends Specification{

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ReactorJavaFallbackSpec'
    ])

    BookClient client = server.applicationContext.getBean(BookClient)

    void "test that fallbacks are called for RxJava responses"() {
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

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Client('/rxjava/fallback/books')
    @Recoverable
    static interface BookClient extends BookApi {
    }

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Fallback
    static class BookFallback implements BookApi {

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
    }

    @Requires(property = 'spec.name', value = 'ReactorJavaFallbackSpec')
    @Controller("/rxjava/fallback/books")
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()

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

    static class Book {
        Long id
        String title
    }
}
