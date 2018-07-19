package io.micronaut.configuration.kafka.offsets

import io.micronaut.configuration.kafka.KafkaConsumerAware
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.OffsetStrategy
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.CollectionUtils
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class AssignToPartitionSpec extends Specification {
    public static final String TOPIC_SYNC = "AssignToPartitionSpec-products-sync"
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, [TOPIC_SYNC]
            )
    )
    void "test manual offset commit"() {
        given:
        ProductClient client = context.getBean(ProductClient)
        ProductListener listener = context.getBean(ProductListener)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        when:
        client.send(new Product(name: "Apple"))
        client.send(new Product(name: "Orange"))
        client.send(new Product(name: "Banana"))

        then:
        conditions.eventually {
            listener.products.size() == 2
            !listener.products.find() { it.name == "Apple"}
            listener.products.find() { it.name == "Orange"}
            listener.products.find() { it.name == "Banana"}
        }
    }

    @KafkaClient
    static interface ProductClient {

        @Topic(ManualOffsetCommitSpec.TOPIC_SYNC)
        void send(Product product)
    }

    @Singleton
    static class ProductListener implements ConsumerRebalanceListener, KafkaConsumerAware {

        List<Product> products = []
        KafkaConsumer kafkaConsumer

        @KafkaListener(
                offsetReset = OffsetReset.EARLIEST
        )

        @Topic(ManualOffsetCommitSpec.TOPIC_SYNC)
        void receive(Product product) {
            products.add(product)
        }

        @Override
        void onPartitionsRevoked(Collection<TopicPartition> partitions) {

        }

        @Override
        void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            for(tp in partitions) {
                kafkaConsumer.seek(tp, 1)
            }
        }

    }

    static class Product {
        String name
    }
}
