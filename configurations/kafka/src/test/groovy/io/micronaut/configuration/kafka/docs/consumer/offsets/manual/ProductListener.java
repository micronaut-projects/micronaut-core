package io.micronaut.configuration.kafka.docs.consumer.offsets.manual;

// tag::imports[]
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.configuration.kafka.docs.consumer.config.Product;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import java.util.Collections;
// end::imports[]

class ProductListener {


    // tag::method[]
    @KafkaListener(
            offsetReset = OffsetReset.EARLIEST,
            offsetStrategy = OffsetStrategy.DISABLED // <1>
    )
    @Topic("awesome-products")
    void receive(
            Product product,
            long offset,
            int partition,
            String topic,
            KafkaConsumer kafkaConsumer) { // <2>
        // process product record

        // commit offsets
        kafkaConsumer.commitSync(Collections.singletonMap( // <3>
                new TopicPartition(topic, partition),
                new OffsetAndMetadata(offset + 1, "my metadata")
        ));

    }
    // end::method[]
}
