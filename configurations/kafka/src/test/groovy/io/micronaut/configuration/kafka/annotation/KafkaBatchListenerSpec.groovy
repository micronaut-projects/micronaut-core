package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.messaging.annotation.SendTo
import io.reactivex.Flowable
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaBatchListenerSpec extends Specification {

    public static final String BOOKS_TOPIC = 'KafkaBatchListenerSpec-books'
    public static final String BOOKS_LIST_TOPIC = 'KafkaBatchListenerSpec-books-list'
    public static final String BOOKS_ARRAY_TOPIC = 'KafkaBatchListenerSpec-books-array'
    public static final String TITLES_TOPIC = 'KafkaBatchListenerSpec-titles'

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS,
                    [ TITLES_TOPIC,
                      BOOKS_LIST_TOPIC,
                      BOOKS_ARRAY_TOPIC,
                      BOOKS_TOPIC
                    ]
            )
    )


    void "test send batch list - blocking"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendBooks([new Book(title: "The Stand"), new Book(title: "The Shining")])

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "The Stand"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }



    void "test send batch array - blocking"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendBooks(new Book(title: "The Stand"), new Book(title: "The Shining"))

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "The Stand"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }

    @io.micronaut.configuration.kafka.annotation.KafkaClient(batch = true)
    @Topic(KafkaBatchListenerSpec.BOOKS_TOPIC)
    static interface MyBatchClient {

        @Topic(KafkaBatchListenerSpec.BOOKS_LIST_TOPIC)
        void sendBooks(List<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_ARRAY_TOPIC)
        void sendBooks(Book...books)

        Flux<Book> sendBooksFlux(Flux<Book> books)

        Flowable<Book> sendBooksFlowable(Flowable<Book> books)

    }

    @KafkaListener(
            batch = true,
            offsetReset = OffsetReset.EARLIEST
    )
    @Topic(KafkaBatchListenerSpec.BOOKS_TOPIC)
    static class BookListener {
        List<Book> books = []

        @Topic(KafkaBatchListenerSpec.BOOKS_LIST_TOPIC)
        void receiveList(List<Book> books) {
            this.books.addAll books
        }

        @Topic(KafkaBatchListenerSpec.BOOKS_ARRAY_TOPIC)
        void receiveArray(Book...books) {
            this.books.addAll Arrays.asList(books)
        }


        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        List<String> receiveAndSendList(List<Book> books) {
            this.books = books
            return books*.title
        }

        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        Book[] receiveAndSendArray(Book...books) {
            this.books = Arrays.asList(books)
            return books*.title as Book[]
        }

        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        Flux<String> receiveAndSendFlux(Flux<Book> books) {
            this.books = books.collectList().block()
            return books.map { Book book -> book.title }
        }

        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        Flowable<String> receiveAndSendFlux(Flowable<Book> books) {
            this.books = books.toList().blockingGet()
            return books.map { Book book -> book.title }
        }
    }

    @ToString(includePackage = false)
    @EqualsAndHashCode
    static class Book {
        String title
    }
}
