package io.micronaut.configuration.kafka.docs.consumer.offsets.rebalance;

import io.micronaut.configuration.kafka.docs.consumer.config.Product;
// tag::imports[]
import io.micronaut.configuration.kafka.KafkaConsumerAware;
import io.micronaut.configuration.kafka.annotation.*;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import javax.annotation.Nonnull;
import java.util.Collection;
// end::imports[]


// tag::clazz[]
@KafkaListener
public class ProductListener implements ConsumerRebalanceListener, KafkaConsumerAware {

    private KafkaConsumer consumer;

    @Override
    public void setKafkaConsumer(@Nonnull KafkaConsumer consumer) { // <1>
        this.consumer = consumer;
    }

    @Topic("awesome-products")
    void receive(Product product) {
        // process product
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) { // <2>
        // save offsets here
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) { // <3>
        // seek to offset here
        for (TopicPartition partition : partitions) {
            consumer.seek(partition, 1);
        }
    }
}
// end::clazz[]