package io.micronaut.configuration.kafka.docs.producer.inject;

import io.micronaut.configuration.kafka.docs.consumer.batch.Book;
// tag::imports[]
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import org.apache.kafka.clients.producer.*;

import javax.inject.Singleton;
import java.util.concurrent.Future;
// end::imports[]

// tag::clazz[]
@Singleton
public class BookSender {

    private final KafkaProducer<String, Book> kafkaProducer;

    public BookSender(
            @KafkaClient("book-producer") KafkaProducer<String, Book> kafkaProducer) { // <1>
        this.kafkaProducer = kafkaProducer;
    }

    public Future<RecordMetadata> send(String author, Book book) {
        return kafkaProducer.send(new ProducerRecord<>("books", author, book)); // <2>
    }

}
// end::clazz[]
