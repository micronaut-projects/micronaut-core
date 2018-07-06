package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.messaging.annotation.Header
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
    public static final String BOOKS_HEADERS_TOPIC = 'KafkaBatchListenerSpec-books-headers'
    public static final String BOOKS_FLUX_TOPIC = 'KafkaBatchListenerSpec-books-flux'
    public static final String BOOKS_FLOWABLE_TOPIC = 'KafkaBatchListenerSpec-books-flowable'
    public static final String BOOKS_FORWARD_LIST_TOPIC = 'KafkaBatchListenerSpec-books-forward-list'
    public static final String BOOKS_FORWARD_ARRAY_TOPIC = 'KafkaBatchListenerSpec-books-forward-array'
    public static final String BOOKS_FORWARD_FLUX_TOPIC = 'KafkaBatchListenerSpec-books-forward-flux'
    public static final String BOOKS_FORWARD_FLOWABLE_TOPIC = 'KafkaBatchListenerSpec-books-forward-flowable'
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
                      BOOKS_TOPIC,
                      BOOKS_FORWARD_LIST_TOPIC
                    ]
            )
    )


    void "test send batch list with headers - blocking"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendBooksAndHeaders([new Book(title: "The Header"), new Book(title: "The Shining")])

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.headers.size() == 2
            bookListener.headers.every() { it == "Bar" }
            bookListener.books.contains(new Book(title: "The Header"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }


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


    void "test send and forward batch list - blocking"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        TitleListener titleListener = context.getBean(TitleListener)

        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendAndForwardBooks([new Book(title: "It"), new Book(title: "Gerald's Game")])

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "It"))
            bookListener.books.contains(new Book(title: "Gerald's Game"))
            titleListener.titles.contains("It")
            titleListener.titles.contains("Gerald's Game")
        }
    }

    void "test send batch array - blocking"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendBooks(new Book(title: "Along Came a Spider"), new Book(title: "The Watchmen"))

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "Along Came a Spider"))
            bookListener.books.contains(new Book(title: "The Watchmen"))
        }
    }


    void "test send and forward batch array - blocking"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        TitleListener titleListener = context.getBean(TitleListener)

        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendAndForward(new Book(title: "Pillars of the Earth"), new Book(title: "War of the World"))

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "Pillars of the Earth"))
            bookListener.books.contains(new Book(title: "War of the World"))
            titleListener.titles.contains("Pillars of the Earth")
            titleListener.titles.contains("War of the World")
        }
    }

    void "test send and forward batch flux"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        Flux<Book> results = myBatchClient.sendAndForwardFlux(Flux.fromIterable([new Book(title: "The Stand"), new Book(title: "The Shining")]))
        results.collectList().block()

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "The Stand"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }

    void "test send and forward batch flowable"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        Flowable<Book> results = myBatchClient.sendAndForwardFlowable(Flowable.fromIterable([new Book(title: "The Flow"), new Book(title: "The Shining")]))
        results.toList().blockingGet()

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "The Flow"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }

    void "test send batch flux"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendBooksFlux(Flux.fromIterable([new Book(title: "The Flux"), new Book(title: "The Shining")]))

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "The Flux"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }


    void "test send batch flowable"() {
        given:
        MyBatchClient myBatchClient = context.getBean(MyBatchClient)
        BookListener bookListener = context.getBean(BookListener)
        bookListener.books?.clear()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.5)

        when:
        myBatchClient.sendBooksFlowable(Flowable.fromIterable([new Book(title: "The Flowable"), new Book(title: "The Shining")]))

        then:
        conditions.eventually {
            bookListener.books.size() == 2
            bookListener.books.contains(new Book(title: "The Flowable"))
            bookListener.books.contains(new Book(title: "The Shining"))
        }
    }

    @io.micronaut.configuration.kafka.annotation.KafkaClient(batch = true)
    @Topic(KafkaBatchListenerSpec.BOOKS_TOPIC)
    static interface MyBatchClient {

        @Topic(KafkaBatchListenerSpec.BOOKS_LIST_TOPIC)
        void sendBooks(List<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_HEADERS_TOPIC)
        @Header(name = "X-Foo", value = "Bar")
        void sendBooksAndHeaders(List<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_LIST_TOPIC)
        void sendAndForwardBooks(List<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_ARRAY_TOPIC)
        void sendBooks(Book...books)

        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_ARRAY_TOPIC)
        void sendAndForward(Book...books)

        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_FLUX_TOPIC)
        Flux<Book> sendAndForwardFlux(Flux<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_FLOWABLE_TOPIC)
        Flowable<Book> sendAndForwardFlowable(Flowable<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_FLUX_TOPIC)
        void sendBooksFlux(Flux<Book> books)

        @Topic(KafkaBatchListenerSpec.BOOKS_FLOWABLE_TOPIC)
        void sendBooksFlowable(Flowable<Book> books)

    }

    @KafkaListener(
            batch = true,
            offsetReset = OffsetReset.EARLIEST
    )
    @Topic(KafkaBatchListenerSpec.BOOKS_TOPIC)
    static class BookListener {
        List<Book> books = []
        List<String> headers = []

        @Topic(KafkaBatchListenerSpec.BOOKS_LIST_TOPIC)
        void receiveList(List<Book> books) {
            this.books.addAll books
        }

        @Topic(KafkaBatchListenerSpec.BOOKS_HEADERS_TOPIC)
        void receiveList(List<Book> books, @Header("X-Foo") List<String> foos) {
            this.books.addAll books
            this.headers = foos
        }

        @Topic(KafkaBatchListenerSpec.BOOKS_ARRAY_TOPIC)
        void receiveArray(Book...books) {
            this.books.addAll Arrays.asList(books)
        }

        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_LIST_TOPIC)
        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        List<String> receiveAndSendList(List<Book> books) {
            this.books.addAll(books)
            return books*.title
        }
        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_ARRAY_TOPIC)
        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        String[] receiveAndSendArray(Book...books) {
            this.books.addAll Arrays.asList(books)
            return books*.title as String[]
        }


        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_FLUX_TOPIC)
        Flux<String> receiveAndSendFlux(Flux<Book> books) {
            this.books.addAll books.collectList().block()
            return books.map { Book book -> book.title }
        }

        @Topic(KafkaBatchListenerSpec.BOOKS_FORWARD_FLOWABLE_TOPIC)
        @SendTo(KafkaBatchListenerSpec.TITLES_TOPIC)
        Flowable<String> receiveAndSendFlowable(Flowable<Book> books) {
            this.books.addAll books.toList().blockingGet()
            return books.map { Book book -> book.title }
        }


        @Topic(KafkaBatchListenerSpec.BOOKS_FLUX_TOPIC)
        void recieveFlux(Flux<Book> books) {
            this.books.addAll books.collectList().block()
        }

        @Topic(KafkaBatchListenerSpec.BOOKS_FLOWABLE_TOPIC)
        void recieveFlowable(Flowable<Book> books) {
            this.books.addAll books.toList().blockingGet()
        }
    }

    @KafkaListener(
            batch = true,
            offsetReset = OffsetReset.EARLIEST
    )
    static class TitleListener {
        List<String> titles = []

        @Topic(KafkaBatchListenerSpec.TITLES_TOPIC)
        void receiveTitles(String...titles) {
            this.titles.addAll(titles)
        }
    }

    @ToString(includePackage = false)
    @EqualsAndHashCode
    static class Book {
        String title
    }
}
