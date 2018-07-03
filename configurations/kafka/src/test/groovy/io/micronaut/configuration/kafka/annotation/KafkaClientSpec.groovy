package io.micronaut.configuration.kafka.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.messaging.annotation.Header
import io.micronaut.messaging.exceptions.MessagingClientException
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class KafkaClientSpec extends Specification {

    void "test send message when Kafka is not available"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MyClient client = ctx.getBean(MyClient)

        when:
        client.sendSync("test", "hello-world")

        then:
        def e = thrown(MessagingClientException)

        cleanup:
        ctx.close()
    }

    void "test reactive send message when Kafka is not available"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MyClient client = ctx.getBean(MyClient)

        when:
        client.sendRx("test", "hello-world").block()

        then:
        def e = thrown(MessagingClientException)

        cleanup:
        ctx.close()

    }

    void "test future send message when Kafka is not available"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        MyClient client = ctx.getBean(MyClient)

        when:
        client.sendSentence("test", "hello-world").get(1, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof MessagingClientException

        cleanup:
        ctx.close()
    }

    @KafkaClient(maxBlock  = '1s', acks = KafkaClient.Acknowledge.ALL)
    static interface MyClient {
        @Topic("words")
        CompletableFuture<String> sendSentence(@KafkaKey String key, String sentence)

        @Topic("words")
        @KafkaClient(
                properties = [
                        @Property(name = ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                                  value = "org.apache.kafka.common.serialization.ByteArraySerializer"),
                        @Property(name = ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                                value = "org.apache.kafka.common.serialization.ByteArraySerializer")
                ]
        )
        String sendSync(@KafkaKey String key, String sentence)

        @Topic("words")
        Mono<String> sendRx(@KafkaKey String key, String sentence)
    }
}
