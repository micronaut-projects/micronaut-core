package io.micronaut.configuration.kafka.annotation

import groovy.transform.NotYetImplemented
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.messaging.annotation.SendTo
import io.reactivex.Flowable
import io.reactivex.Single
import org.apache.kafka.clients.producer.RecordMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ConcurrentLinkedDeque

class KafkaSendToSpec extends Specification {

    public static final String TOPIC_NAME = "KafkaSendToSpec-products"
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            Collections.singletonMap(
                    AbstractKafkaConfiguration.EMBEDDED, true
            )

    )

    @NotYetImplemented
    void "test send to another topic"() {
        given:
        ProductClient client = context.getBean(ProductClient)
        ProductListener productListener = context.getBean(ProductListener)
        QuantityListener quantityListener = context.getBean(QuantityListener)
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)


        when:
        client.sendProduct("Apple", new Product(name: "Apple", quantity: 5))

        then:
        conditions.eventually {
            productListener.products.size() == 1
            productListener.products.iterator().next().name == "Apple"
            quantityListener.quantities.size() == 1
            quantityListener.quantities.iterator().next() == 5
        }
    }


    @KafkaClient(acks = KafkaClient.Acknowledge.ALL)
    @Topic(KafkaSendToSpec.TOPIC_NAME)
    static interface ProductClient {
        RecordMetadata sendProduct(@KafkaKey String name, Product product)
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    @Topic(KafkaSendToSpec.TOPIC_NAME)
    static class ProductListener {
        Queue<Product> products = new ConcurrentLinkedDeque<>()


        @SendTo("quantity")
        Single<Integer> receiveSingle(Single<Product> product) {
            product.map({ Product p -> p.quantity })
        }

        @SendTo("quantity")
        Flowable<Integer> receiveFlowable(Flowable<Product> product) {
            product.map({ Product p -> p.quantity })
        }

        @SendTo("quantity")
        Flux<Integer> receiveFlux(Flux<Product> product) {
            product.map({ Product p -> p.quantity })
        }

        @SendTo("quantity")
        Mono<Integer> receiveMono(Mono<Product> product) {
            product.map({ Product p -> p.quantity })
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    @Topic("quantity")
    static class QuantityListener {
        Queue<Integer> quantities = new ConcurrentLinkedDeque<>()

        void recieveQuantity(int q) {
            quantities.add(q)
        }
    }

    static class Product {
        String name
        int quantity
    }
}
