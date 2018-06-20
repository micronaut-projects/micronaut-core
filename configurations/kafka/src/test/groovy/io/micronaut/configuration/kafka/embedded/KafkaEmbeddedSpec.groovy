package io.micronaut.configuration.kafka.embedded

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.configuration.kafka.config.AbstractKafkaConsumerConfiguration
import io.micronaut.context.ApplicationContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import spock.lang.Specification

class KafkaEmbeddedSpec extends Specification{

    void "test run kafka embedded server"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )
        )

        when:
        AbstractKafkaConsumerConfiguration config = applicationContext.getBean(AbstractKafkaConsumerConfiguration)
        Properties props = config.getConfig()

        then:
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] == AbstractKafkaConfiguration.DEFAULT_BOOTSTRAP_SERVERS


        when:
        KafkaEmbedded kafkaEmbedded = applicationContext.getBean(KafkaEmbedded)

        then:
        kafkaEmbedded.kafkaServer.isPresent()

        cleanup:
        applicationContext.close()
    }
}
