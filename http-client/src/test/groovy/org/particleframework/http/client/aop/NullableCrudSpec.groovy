package org.particleframework.http.client.aop

import org.particleframework.context.ApplicationContext
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Delete
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Patch
import org.particleframework.http.annotation.Post
import org.particleframework.http.client.Client
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong


class NullableCrudSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test CRUD operations on generated client that returns blocking responses"() {
        given:
        NullableBookClient client = context.getBean(NullableBookClient)

        when:
        NullableBook book = client.get(99)
        List<NullableBook> books = client.list()

        then:
        book == null
        books.size() == 0

        when:
        book = client.save("The Stand")

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.get(book.id)

        then:
        book != null
        book.title == "The Stand"
        book.id == 1

        when:
        book = client.update(book.id, "The Shining")

        then:
        book != null
        book.title == "The Shining"
        book.id == 1

        when:
        client.delete(book.id)

        then:
        client.get(book.id) == null
    }

    void "test DELETE operation with null values"() {
        given:
        NullableBookClient client = context.getBean(NullableBookClient)

        when:
        client.delete(null)

        then:
        noExceptionThrown()
    }

    void "test POST operation with null values"() {
        given:
        NullableBookClient client = context.getBean(NullableBookClient)

        when:
        NullableBook book = client.save(null)

        then:
        book.title == null
        noExceptionThrown()
    }

    void "test PUT operation with null values"() {
        given:
        NullableBookClient client = context.getBean(NullableBookClient)

        when:
        NullableBook saved = client.save("Temporary")
        NullableBook book = client.update(saved.id, null)

        then:
        book.title == null
        noExceptionThrown()
    }

    void "test GET operation with null values"() {
        given:
        NullableBookClient client = context.getBean(NullableBookClient)

        when:
        NullableBook book = client.get(null)

        then:
        book == null
        noExceptionThrown()
    }

    @Client('/blocking/nullableBooks')
    static interface NullableBookClient extends NullableBookApi {
    }

    @Controller("/blocking/nullableBooks")
    @Singleton
    static class NullableBookController implements NullableBookApi {

        Map<Long, NullableBook> books = new LinkedHashMap<>()
        AtomicLong currentId = new AtomicLong(0)

        @Override
        NullableBook get(@Nullable Long id) {
            return books.get(id)
        }

        @Override
        List<NullableBook> list() {
            return books.values().toList()
        }

        @Override
        void delete(@Nullable Long id) {
            books.remove(id)
        }

        @Override
        NullableBook save(@Nullable String title) {
            NullableBook book = new NullableBook(title: title, id: currentId.incrementAndGet())
            books[book.id] = book
            return book
        }

        @Override
        NullableBook update(Long id, @Nullable String title) {
            NullableBook book = books[id]
            if (book != null) {
                book.title = title
            }
            return book
        }
    }

    static interface NullableBookApi {

        @Get("/show{/id}") // /show to avoid calling list instead
        NullableBook get(@Nullable Long id)

        @Get('/')
        List<NullableBook> list()

        @Delete("{/id}")
        void delete(@Nullable Long id)

        @Post('/')
        NullableBook save(@Nullable String title)

        @Patch("/{id}")
        NullableBook update(Long id, @Nullable String title)
    }


    static class NullableBook {
        Long id
        String title
    }
}