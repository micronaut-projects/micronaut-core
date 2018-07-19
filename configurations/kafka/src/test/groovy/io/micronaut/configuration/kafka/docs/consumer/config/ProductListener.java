package io.micronaut.configuration.kafka.docs.consumer.config;

// tag::imports[]
import io.micronaut.configuration.kafka.annotation.*;
import io.micronaut.context.annotation.Property;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    @Topic("awesome-products")
    public void receive(
            @KafkaKey String brand, // <1>
            Product product, // <2>
            long offset, // <3>
            int partition, // <4>
            String topic, // <5>
            long timestamp) { // <6>
        System.out.println("Got Product - " + product.getName() + " by " + brand);
    }
    // end::method[]

    // tag::consumeRecord[]
    @Topic("awesome-products")
    public void receive(ConsumerRecord<String, Product> record) { // <1>
        Product product = record.value(); // <2>
        String brand = record.key(); // <3>
        System.out.println("Got Product - " + product.getName() + " by " + brand);
    }
    // end::consumeRecord[]
}