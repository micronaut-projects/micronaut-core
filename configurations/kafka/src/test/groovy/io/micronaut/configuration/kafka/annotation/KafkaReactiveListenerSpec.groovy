package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.messaging.MessageHeaders
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class KafkaReactiveListenerSpec extends Specification{

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            Collections.singletonMap(
                    AbstractKafkaConfiguration.EMBEDDED, true
            )

    )

    void "test receive single"() {

    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class PojoConsumer {
        Book lastBook

        @Topic("books")
        void receiveBook(Single<Book> book) {
            lastBook = book.blockingGet()
        }

        @Topic("books-flowable")
        void receiveBook(Flowable<Book> book) {
            lastBook = book.firstElement().blockingGet()
        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
