package io.micronaut.configuration.kafka.annotation

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.messaging.annotation.SendTo
import io.reactivex.Flowable
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class KafkaBatchListenerSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, ["titles", "books"]
            )
    )

    @io.micronaut.configuration.kafka.annotation.KafkaClient(batch = true)
    @Topic('books')
    static interface KafkaClient {

        void sendBooks(List<Book> books)

        void sendBooks(Book...books)

        Flux<Book> sendBooksFlux(Flux<Book> books)

        Flowable<Book> sendBooksFlowable(Flowable<Book> books)

    }

    @KafkaListener(batch = true)
    @Topic("books")
    static class BookListener {
        List<Book> books

        void receiveList(List<Book> books) {
            this.books = books
        }

        void receiveArray(Book...books) {
            this.books = Arrays.asList(books)
        }


        @SendTo("titles")
        List<String> receiveAndSendList(List<Book> books) {
            this.books = books
            return books*.title
        }

        @SendTo("titles")
        Book[] receiveAndSendArray(Book...books) {
            this.books = Arrays.asList(books)
            return books*.title as Book[]
        }

        @SendTo("titles")
        Flux<String> receiveAndSendFlux(Flux<Book> books) {
            this.books = books.collectList().block()
            return books.map { Book book -> book.title }
        }

        @SendTo("titles")
        Flowable<String> receiveAndSendFlux(Flowable<Book> books) {
            this.books = books.collectList().block()
            return books.map { Book book -> book.title }
        }
    }

    static class Book {
        String title
    }
}
