package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.reactivex.Flowable
import io.reactivex.Single
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future

class KafkaReactiveListenerSpec extends Specification{

    public static final String TOPIC_NAME = "KafkaReactiveListenerSpec-books"
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, [TOPIC_NAME]
            )


    )

    void "test send and return single"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        Book book = bookClient.sendSingle("Stephen King", Single.just(new Book(title: "It"))).blockingGet()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        book.title == "It"
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == book
        }
    }

    void "test send and return mono"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        Book book = bookClient.sendMono("Stephen King", Mono.just(new Book(title: "It"))).block()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        book.title == "It"
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == book
        }
    }

    void "test send and return flowable"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flowable.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<Book> book = bookClient.sendFlowable("Stephen King", flowable).toList().blockingGet()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        book.size() == 2
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }

    void "test send and return flux"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flux.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<Book> book = bookClient.sendFlux("Stephen King", flowable).collectList().block()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        book.size() == 2
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }



    void "test send and return single record metadata"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        RecordMetadata recordMetadata = bookClient.sendSingleGetRecord("Stephen King", Single.just(new Book(title: "It"))).blockingGet()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        recordMetadata != null
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return mono record metadata"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        RecordMetadata recordMetadata = bookClient.sendMonoGetRecord("Stephen King", Mono.just(new Book(title: "It"))).block()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        recordMetadata != null
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return flowable record metadata"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flowable.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<RecordMetadata> result = bookClient.sendFlowableGetRecord("Stephen King", flowable).toList().blockingGet()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        result.size() == 2
        result.every { it instanceof RecordMetadata }
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }

    void "test send and return flux record metadata"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flux.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<RecordMetadata> result = bookClient.sendFluxGetRecord("Stephen King", flowable).collectList().block()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        result.size() == 2
        result.every { it instanceof RecordMetadata }
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }



    void "test send and return single record metadata - future"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        RecordMetadata recordMetadata = bookClient.sendSingleFuture("Stephen King", Single.just(new Book(title: "It"))).get()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        recordMetadata != null
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return mono record metadata - future"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        RecordMetadata recordMetadata = bookClient.sendMonoFuture("Stephen King", Mono.just(new Book(title: "It"))).get()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        recordMetadata != null
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return flowable record metadata - future"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flowable.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<RecordMetadata> result = bookClient.sendFlowableFuture("Stephen King", flowable).get()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        result.size() == 2
        result.every { it instanceof RecordMetadata }
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }

    void "test send and return flux record metadata - future"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flux.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<RecordMetadata> result = bookClient.sendFluxFuture("Stephen King", flowable).get()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        result.size() == 2
        result.every { it instanceof RecordMetadata }
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }


    void "test send and return single record metadata - block"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        RecordMetadata recordMetadata = bookClient.sendSingleRM("Stephen King", Single.just(new Book(title: "It")))
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        recordMetadata != null
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return mono record metadata - block"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        RecordMetadata recordMetadata = bookClient.sendMonoRM("Stephen King", Mono.just(new Book(title: "It")))
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        recordMetadata != null
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return flowable record metadata - block"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flowable.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<RecordMetadata> result = bookClient.sendFlowableRM("Stephen King", flowable).toList()
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        result.size() == 2
        result.every { it instanceof RecordMetadata }
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }

    void "test send and return flux record metadata - block"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flux.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        List<RecordMetadata> result = bookClient.sendFluxRM("Stephen King", flowable)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        result.size() == 2
        result.every { it instanceof RecordMetadata }
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }


    void "test send and return single record metadata - block void"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        bookClient.sendSingleVoid("Stephen King", Single.just(new Book(title: "It")))
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return mono record metadata - block void"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()

        when:
        bookClient.sendMonoVoid("Stephen King", Mono.just(new Book(title: "It")))
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        conditions.eventually {
            listener.books.size() == 1
            listener.books.iterator().next() == new Book(title: "It")
        }
    }

    void "test send and return flowable record metadata - block void"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flowable.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        bookClient.sendFlowableVoid("Stephen King", flowable)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }

    void "test send and return flux record metadata - block void"() {
        given:
        BookClient bookClient = context.getBean(BookClient)
        BookListener listener = context.getBean(BookListener)
        listener.books.clear()
        def flowable = Flux.just(new Book(title: "It"), new Book(title:  "The Shining"))

        when:
        bookClient.sendFluxVoid("Stephen King", flowable)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then:
        conditions.eventually {
            listener.books.contains(new Book(title: "It"))
            listener.books.contains(new Book(title:  "The Shining"))
        }
    }

    @KafkaClient(acks = KafkaClient.Acknowledge.ALL)
    @Topic(KafkaReactiveListenerSpec.TOPIC_NAME)
    static interface BookClient {

        Single<Book> sendSingle(@KafkaKey String author, Single<Book> book)

        Flowable<Book> sendFlowable(@KafkaKey String author, Flowable<Book> book)

        Flux<Book> sendFlux(@KafkaKey String author, Flux<Book> book)

        Mono<Book> sendMono(@KafkaKey String author, Mono<Book> book)

        Single<RecordMetadata> sendSingleGetRecord(@KafkaKey String author, Single<Book> book)

        Flowable<RecordMetadata> sendFlowableGetRecord(@KafkaKey String author, Flowable<Book> book)

        Flux<RecordMetadata> sendFluxGetRecord(@KafkaKey String author, Flux<Book> book)

        Mono<RecordMetadata> sendMonoGetRecord(@KafkaKey String author, Mono<Book> book)

        void sendSingleVoid(@KafkaKey String author, Single<Book> book)

        void sendFlowableVoid(@KafkaKey String author, Flowable<Book> book)

        void sendFluxVoid(@KafkaKey String author, Flux<Book> book)

        void sendMonoVoid(@KafkaKey String author, Mono<Book> book)

        RecordMetadata sendSingleRM(@KafkaKey String author, Single<Book> book)

        List<RecordMetadata> sendFlowableRM(@KafkaKey String author, Flowable<Book> book)

        List<RecordMetadata> sendFluxRM(@KafkaKey String author, Flux<Book> book)

        RecordMetadata sendMonoRM(@KafkaKey String author, Mono<Book> book)

        Future sendSingleFuture(@KafkaKey String author, Single<Book> book)

        Future sendFlowableFuture(@KafkaKey String author, Flowable<Book> book)

        Future sendFluxFuture(@KafkaKey String author, Flux<Book> book)

        Future sendMonoFuture(@KafkaKey String author, Mono<Book> book)
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    @Topic(KafkaReactiveListenerSpec.TOPIC_NAME)
    static class BookListener {
        Queue<Book> books = new ConcurrentLinkedDeque<>()


        void receiveSingle(Single<Book> book) {
            books.add( book.blockingGet() )
        }

        void receiveFlowable(Flowable<Book> book) {
            books.addAll book.toList().blockingGet()
        }

        void receiveFlux(Flux<Book> book) {
            books.add(book.blockFirst())
        }

        void receiveMono(Mono<Book> book) {
            books.add(book.block())
        }
    }

    @EqualsAndHashCode
    @ToString(includePackage = false)
    static class Book {
        String title
    }
}
