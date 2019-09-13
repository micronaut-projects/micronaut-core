package io.micronaut.http.server.netty.xml

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class XmlContentProcessorSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [:])

    void "test sending a single book"() {
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = client.retrieve(
                HttpRequest.POST("/xml/stream", '<book><title>First Book</title></book>')
                        .contentType(MediaType.TEXT_XML_TYPE), Book.class).toList().blockingGet()

        then:
        books.size() == 1
        books[0].title == "First Book"
    }

    void "test sending a list of books"() {
        RxStreamingHttpClient client = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = client.jsonStream(
                HttpRequest.POST("/xml/stream/list", '<books><book><title>First Book</title></book><book><title>Second Book</title></book></books>')
                        .contentType(MediaType.TEXT_XML_TYPE), Book.class).toList().blockingGet()

        then:
        books.size() == 2
        books[0].title == "First Book"
        books[1].title == "Second Book"
    }

    void "test streaming books"() {
        RxStreamingHttpClient client = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = client.jsonStream(
                HttpRequest.POST("/xml/stream", '<books><book><title>First Book</title></book><book><title>Second Book</title></book></books>')
                        .contentType(MediaType.TEXT_XML_TYPE), Book.class).toList().blockingGet()

        then:
        books.size() == 2
        books[0].title == "First Book"
        books[1].title == "Second Book"
    }

    void "test streaming books with self closed elements"() {
        RxStreamingHttpClient client = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = client.jsonStream(
                HttpRequest.POST("/xml/stream", '<books><book title="First Book"/><book title="Second Book"/></books>')
                        .contentType(MediaType.TEXT_XML_TYPE), Book.class).toList().blockingGet()

        then:
        books.size() == 2
        books[0].title == "First Book"
        books[1].title == "Second Book"
    }

    void "test sending a blocking author"() {
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        Author author = client.toBlocking().retrieve(
                HttpRequest.POST("/xml/stream/author", '<author name="Joe"><books><book><title>First Book</title></book></books></author>')
                        .contentType(MediaType.TEXT_XML_TYPE), Author.class)

        then:
        author.books.size() == 1
        author.books[0].title == "First Book"
        author.name == "Joe"
    }

    void "test sending a blocking author with 2 books"() {
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        Author author = client.toBlocking().retrieve(
                HttpRequest.POST("/xml/stream/author", '<author name="Joe"><books><book><title>First Book</title></book><book><title>Second Book</title></book></books></author>')
                        .contentType(MediaType.TEXT_XML_TYPE), Author.class)

        then:
        author.books.size() == 2
        author.books[0].title == "First Book"
        author.books[1].title == "Second Book"
        author.name == "Joe"
    }

    void "test mapping xml simple field to controller argument"() {
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        String name = client.toBlocking().retrieve(
                HttpRequest.POST("/xml/stream/author/name", '<author name="Joe"><books><book><title>First Book</title></book></books></author>')
                        .contentType(MediaType.TEXT_XML_TYPE))

        then:
        name == 'Joe'
    }

    void "test mapping xml list to controller argument"() {
        RxStreamingHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        List<Book> books = client.jsonStream(
                HttpRequest.POST("/xml/stream/author/books", '<author name="Joe"><books><book><title>First Book</title></book><book><title>Second Book</title></book></books></author>')
                        .contentType(MediaType.TEXT_XML_TYPE)).toList().blockingGet();

        then:
        books.size() == 2
        books[0].title == "First Book"
        books[1].title == "Second Book"
    }

    void "test mapping xml set to controller argument"() {
        RxStreamingHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        Set<String> books = client.jsonStream(
                HttpRequest.POST("/xml/stream/author/bookSet", '<author name="Joe"><books><book><title>First Book</title></book><book><title>Second Book</title></book></books></author>')
                        .contentType(MediaType.TEXT_XML_TYPE))
                .toList()
                .blockingGet()
                .stream()
                .map{book -> book.title}
                .collect(Collectors.toSet())

        then:
        books.size() == 2
        books.contains "First Book"
        books.contains "Second Book"
    }

    @Controller(value = "/xml/stream", consumes = MediaType.TEXT_XML)
    static class StreamController {

        @Post
        Flowable<Book> stream(@Body Flowable<Book> books) {
            return books
        }

        @Post("/list")
        Flowable<Book> streamList(@Body Flowable<List<Book>> books) {
            return books.flatMap({ bookList ->
                return Flowable.fromIterable(bookList)
            })
        }

        @Post("/author")
        Author author(@Body Author author) {
            return author
        }

        @Post("/author/name")
        String author(String name) {
            return name
        }

        @Post("/author/books")
        Flowable<Book> bookList(List<Book> books) {
            return Flowable.fromIterable(books)
        }

        @Post("/author/bookSet")
        Flowable<Book> bookList(Set<Book> books) {
            return Flowable.fromIterable(books)
        }
    }

    public static class Book {
        String title
    }

    static class Author {
        String name
        List<Book> books
    }
}
