package io.micronaut.configuration.kafka.annotation

import io.micronaut.configuration.kafka.AbstractKafkaConfiguration
import io.micronaut.configuration.kafka.AbstractKafkaProducerConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaConsumerSpec extends Specification {


    void "test simple consumer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("words", "key", "hello world")).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        MyConsumer myConsumer = context.getBean(MyConsumer)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
        }

        cleanup:
        producer.close()
        context.close()

    }


    @KafkaConsumer(
            properties = @Property(
                name = "auto.offset.reset",
                value = "earliest"
            )
    )
    static class MyConsumer {
        int wordCount

        @Topic("words")
        void countWord(String sentence) {
            wordCount += sentence.split(/\s/).size()
        }
    }
}
