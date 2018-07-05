package io.micronaut.configuration.kafka.docs.producer.config;

// tag::imports[]
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.context.annotation.Property;
import org.apache.kafka.clients.producer.ProducerConfig;
// end::imports[]

// tag::clazz[]
@KafkaClient(
    id="product-client",
    acks = KafkaClient.Acknowledge.ALL,
    properties = @Property(name = ProducerConfig.RETRIES_CONFIG, value = "5")
)
public interface ProductClient {
// end::clazz[]
}
