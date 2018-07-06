package io.micronaut.configuration.kafka.docs.quickstart;

// tag::imports[]
import io.micronaut.configuration.kafka.annotation.*;
// end::imports[]

// tag::clazz[]
@KafkaClient // <1>
public interface ProductClient {

    @Topic("my-products") // <2>
    void sendProduct(@KafkaKey String brand, String name); // <3>
}
// end::clazz[]
