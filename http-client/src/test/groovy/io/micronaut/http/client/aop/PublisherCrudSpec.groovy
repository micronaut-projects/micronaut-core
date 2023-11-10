package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong

/**
 * @author graemerocher
 * @since 1.0
 */
class PublisherCrudSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name':'PublisherCrudSpec',
    ])

    BookClient client = server.applicationContext.getBean(BookClient)

    void "test CRUD operations on generated client that returns blocking responses"() {
        when:
        Book book = Flux.from(client.get(99))
                .onErrorResume(t -> { Flux.empty()})
                .next()
                .block()
        List<Book> books = Flux.from(client.list()).blockFirst()

        then:
        book == null
        books.size() == 0

        when:
        book = Flux.from(client.save("The Stand")).blockFirst()

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = Flux.from(client.get(book.id)).blockFirst()

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = Flux.from(client.update(book.id, "The Shining")).blockFirst()

        then:
        book != null
        book.title == "The Shining"
        book.id == 1

        when:
        Flux.from(client.delete(book.id)).blockFirst()
        book = Flux.from(client.get(book.id))
                .onErrorResume(t -> { Flux.empty()})
                .next()
                .block()
        then:
        book == null
    }

    @Client('/publisher/books')
    @Requires(property = 'spec.name', value = 'PublisherCrudSpec')
    static interface BookClient extends BookApi {

    }

    @Controller("/publisher/books")
    @Requires(property = 'spec.name', value = 'PublisherCrudSpec')
    static class BookController implements BookApi {

        Map<Long, Book> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        Publisher<Book> get(Long id) {
            return Publishers.just(books.get(id))
        }

        @Override
        Publisher<List<Book>> list() {
            return Publishers.just(books.values().toList())
        }

        @Override
        Publisher<Book> delete(Long id) {
            return Publishers.just(books.remove(id))
        }

        @Override
        Publisher<Book> save(String title) {
            Book book = new Book(title: title, id:currentId.incrementAndGet())
            books[book.id] = book
            return Publishers.just(book)
        }

        @Override
        Publisher<Book> update(Long id, String title) {
            Book book = books[id]
            if(book != null) {
                book.title = title
            }
            return Publishers.just(book)
        }
    }

    static interface BookApi {

        @Get(uri = "/{id}", single = true)
        Publisher<Book> get(Long id)

        @Get(single = true)
        Publisher<List<Book>> list()

        @Delete(uri = "/{id}", single = true)
        Publisher<Book> delete(Long id)

        @Post(single = true)
        Publisher<Book> save(String title)

        @Patch(uri = "/{id}", single = true)
        Publisher<Book> update(Long id, String title)
    }

    static class Book {
        Long id
        String title
    }
}

