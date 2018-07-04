package io.micronaut.configuration.kafka.docs.consumer.config;

// tag::imports[]
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.context.annotation.Property;
import org.apache.kafka.clients.consumer.ConsumerConfig;
// end::imports[]

// tag::clazz[]
@KafkaListener(
    groupId = "products",
    pollTimeout = "500ms",
    properties = @Property(name = ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, value = "5000")
)
public class ProductListener {
// end::clazz[]

    // tag::method[]
    @Topic("awesome-products") // <2>
    public void receive(@KafkaKey String brand, String name) { // <3>
        System.out.println("Got Product - " + name + " by " + brand);
    }
    // end::method[]
}