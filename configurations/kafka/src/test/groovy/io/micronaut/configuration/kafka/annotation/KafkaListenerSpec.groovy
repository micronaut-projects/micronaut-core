package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.configuration.kafka.config.AbstractKafkaProducerConfiguration
import io.micronaut.configuration.kafka.metrics.KafkaConsumerMetrics
import io.micronaut.configuration.kafka.metrics.KafkaProducerMetrics
import io.micronaut.configuration.kafka.serde.JsonSerde
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import io.micronaut.messaging.MessageHeaders
import io.micronaut.messaging.annotation.Header
import io.reactivex.Single
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

@Stepwise
class KafkaListenerSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, ["words", "books", "words-records", "books-records"]
            )
    )

    void "test simple consumer"() {
        when:
        MyClient myClient = context.getBean(MyClient)
        myClient.sendSentence("key", "hello world", "words")

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        MyConsumer myConsumer = context.getBean(MyConsumer)

        then:
        context.containsBean(KafkaConsumerMetrics)
        context.containsBean(KafkaProducerMetrics)
        conditions.eventually {
            myConsumer.wordCount == 2
            myConsumer.lastTopic == 'words'
        }
    }


    void "test POJO consumer"() {
        when:
        MyClient myClient = context.getBean(MyClient)
        Book book = myClient.sendReactive("Stephen King", new Book(title: "The Stand")).blockingGet()

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        PojoConsumer myConsumer = context.getBean(PojoConsumer)
        then:
        conditions.eventually {
            myConsumer.lastBook == book
            myConsumer.messageHeaders != null
        }
    }


    void "test @KafkaKey annotation"() {
        when:
        MyClient myClient = context.getBean(MyClient)
        RecordMetadata metadata = myClient.sendGetRecordMetadata("key", "hello world")

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        MyConsumer2 myConsumer = context.getBean(MyConsumer2)
        then:
        metadata != null
        metadata.topic() == "words"
        conditions.eventually {
            myConsumer.wordCount == 4
            myConsumer.key == "key"
        }

    }

    void "test receive ConsumerRecord"() {
        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        config.setKeySerializer(new StringSerializer())
        config.setValueSerializer(new StringSerializer())
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("words-records", "key", "hello world")).get()

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        MyConsumer3 myConsumer = context.getBean(MyConsumer3)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
            myConsumer.key == "key"
        }

        cleanup:
        producer.close()

    }



    void "test POJO consumer record"() {

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        config.setKeySerializer(new StringSerializer())
        config.setValueSerializer(new JsonSerde(Book).serializer())
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("books-records", "Stephen King", new Book(title: "The Stand"))).get()

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        PojoConsumer2 myConsumer = context.getBean(PojoConsumer2)
        then:
        conditions.eventually {
            myConsumer.lastBook == new Book(title: "The Stand")
            myConsumer.topic == "books-records"
            myConsumer.offset != null
        }

        cleanup:
        producer.close()

    }

    @KafkaClient
    static interface MyClient {
        @Topic("words")
        void sendSentence(@KafkaKey String key, String sentence, @Header String topic)

        @Topic("words")
        RecordMetadata sendGetRecordMetadata(@KafkaKey String key, String sentence)

        @Topic("books")
        Single<Book> sendReactive(@KafkaKey String key, Book book)
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class MyConsumer {
        int wordCount
        String lastTopic

        @Topic("words")
        void countWord(String sentence, @Header String topic) {
            wordCount += sentence.split(/\s/).size()
            lastTopic = topic
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
    static class MyConsumer3 {
        int wordCount
        String key

        @Topic("words-records")
        void countWord(@KafkaKey String key, ConsumerRecord<String, String> record) {
            wordCount += record.value().split(/\s/).size()
            this.key = key
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class PojoConsumer2 {
        Book lastBook
        String topic
        Long offset

        @Topic("books-records")
        void receiveBook(String topic, long offset, ConsumerRecord<String, Book> record) {
            lastBook = record.value()
            this.topic = topic
            this.offset = offset
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class PojoConsumer {
        Book lastBook
        MessageHeaders messageHeaders

        @Topic("books")
        void receiveBook(Book book, MessageHeaders messageHeaders) {
            lastBook = book
            this.messageHeaders = messageHeaders

        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
