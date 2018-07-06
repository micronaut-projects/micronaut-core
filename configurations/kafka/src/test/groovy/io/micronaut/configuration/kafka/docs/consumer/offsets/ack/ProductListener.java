package io.micronaut.configuration.kafka.docs.consumer.offsets.ack;

import io.micronaut.configuration.kafka.Acknowledgement;
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.configuration.kafka.docs.consumer.config.Product;

class ProductListener {


    // tag::method[]
    @KafkaListener(
        offsetReset = OffsetReset.EARLIEST,
        offsetStrategy = OffsetStrategy.DISABLED // <1>
    )
    @Topic("awesome-products")
    void receive(
            Product product,
            Acknowledgement acknowledgement) { // <2>
        // process product record

        acknowledgement.ack(); // <3>
    }
    // end::method[]
}
