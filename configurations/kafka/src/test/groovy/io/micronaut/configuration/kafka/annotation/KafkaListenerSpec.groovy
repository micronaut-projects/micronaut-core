package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.configuration.kafka.config.AbstractKafkaProducerConfiguration
import io.micronaut.configuration.kafka.serde.JsonSerde
import io.micronaut.context.ApplicationContext
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaListenerSpec extends Specification {


    void "test simple consumer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("words", "key", "hello world")).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        MyConsumer myConsumer = context.getBean(MyConsumer)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
        }

        cleanup:
        producer.close()
        context.close()

    }


    void "test POJO consumer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        config.setKeySerializer(new StringSerializer())
        config.setValueSerializer(new JsonSerde(Book).serializer())
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("books", "Stephen King", new Book(title: "The Stand"))).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        PojoConsumer myConsumer = context.getBean(PojoConsumer)
        then:
        conditions.eventually {
            myConsumer.lastBook == new Book(title: "The Stand")
        }

        cleanup:
        producer.close()
        context.close()

    }


    void "test @KafkaKey annotation"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("words", "key", "hello world")).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        MyConsumer2 myConsumer = context.getBean(MyConsumer2)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
            myConsumer.key == "key"
        }

        cleanup:
        producer.close()
        context.close()

    }


    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class MyConsumer {
        int wordCount

        @Topic("words")
        void countWord(String sentence) {
            wordCount += sentence.split(/\s/).size()
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class MyConsumer2 {
        int wordCount
        String key

        @Topic("words")
        void countWord(@KafkaKey String key, String sentence) {
            wordCount += sentence.split(/\s/).size()
            this.key = key
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class PojoConsumer {
        Book lastBook

        @Topic("books")
        void receiveBook(Book book) {
            lastBook = book
        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
