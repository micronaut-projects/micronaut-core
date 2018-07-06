package io.micronaut.configuration.kafka.docs.consumer.batch;

// tag::imports[]
import io.micronaut.configuration.kafka.annotation.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
// end::imports[]

// tag::clazz[]
@KafkaListener(batch = true) // <1>
public class BookListener {
// end::clazz[]

    // tag::method[]
    @Topic("all-the-books")
    public void receiveList(List<Book> books) { // <2>
        for (Book book : books) {
            System.out.println("Got Book = " + book.getTitle()); // <3>
        }
    }
    // end::method[]


    // tag::reactive[]
    @Topic("all-the-books")
    public Flux<Book> receiveFlux(Flux<Book> books) {
        return books.doOnNext(book ->
                System.out.println("Got Book = " + book.getTitle())
        );
    }
    // end::reactive[]

    // tag::manual[]
    @Topic("all-the-books")
    public void receive(
            List<Book> books,
            List<Long> offsets,
            List<Integer> partitions,
            List<String> topics,
            KafkaConsumer kafkaConsumer) { // <1>
        for (int i = 0; i < books.size(); i++) {

            // process the book
            Book book = books.get(i); // <2>

            // commit offsets
            String topic = topics.get(i);
            int partition = partitions.get(i);
            long offset = offsets.get(i); // <3>

            kafkaConsumer.commitSync(Collections.singletonMap( // <4>
                    new TopicPartition(topic, partition),
                    new OffsetAndMetadata(offset + 1, "my metadata")
            ));

        }
    }
    // end::manual[]

}
