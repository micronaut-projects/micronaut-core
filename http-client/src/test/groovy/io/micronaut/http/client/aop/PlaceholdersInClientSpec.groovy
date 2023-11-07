package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class PlaceholdersInClientSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'BlockingCrudSpec',
            'books.uri': '/blocking',
            'my.path'  : '{id}'
    ])

    BookClient client = server.applicationContext.getBean(BookClient)

    void "test placeholder in @Client value"() {
        when:
        BlockingCrudSpec.Book book = client.get(99)
        List<BlockingCrudSpec.Book> books = client.list()

        then:
        book == null
        books.size() == 0

        when:
        book = client.save("The Stand")

        then:
        book != null
        book.title == "The Stand"
        book.id == 1
        client.getOther(book.id).title == book.title
    }

    @Client('${books.uri}/books')
    @Requires(property = 'spec.name', value = 'BlockingCrudSpec')
    static interface BookClient extends BlockingCrudSpec.BookApi {

        @Get('/${my.path}')
        BlockingCrudSpec.Book getOther(Long id)
    }
}
