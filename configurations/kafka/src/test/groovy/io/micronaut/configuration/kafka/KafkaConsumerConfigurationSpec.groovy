package io.micronaut.configuration.kafka

import io.micronaut.context.ApplicationContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import spock.lang.Specification

class KafkaConsumerConfigurationSpec extends Specification {



    void "test default consumer configuration"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        when:
        KafkaConsumerConfiguration config = applicationContext.getBean(KafkaConsumerConfiguration)
        Properties props = config.getConfig()

        then:
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] == AbstractKafkaConfiguration.DEFAULT_BOOTSTRAP_SERVERS

        when:
        KafkaConsumer consumer = applicationContext.createBean(KafkaConsumer, config)

        then:
        consumer != null

        cleanup:
        consumer.close()
        applicationContext.close()
    }



    void "test configure default properties"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                ("kafka.${ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG}".toString()):"localhost:1111",
                ("kafka.${ConsumerConfig.GROUP_ID_CONFIG}".toString()):"mygroup",
                ("kafka.${ConsumerConfig.MAX_POLL_RECORDS_CONFIG}".toString()):"100"
        )

        when:
        KafkaConsumerConfiguration config = applicationContext.getBean(KafkaConsumerConfiguration)
        Properties props = config.getConfig()

        then:
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] == "localhost:1111"
        props[ConsumerConfig.GROUP_ID_CONFIG] == "mygroup"
        props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] == "100"

        when:
        KafkaConsumer consumer = applicationContext.createBean(KafkaConsumer, config)

        then:
        consumer != null

        cleanup:
        consumer.close()
        applicationContext.close()
    }

    void "test override consumer default properties"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                ("kafka.${ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG}".toString()):"localhost:1111",
                ("kafka.${ConsumerConfig.GROUP_ID_CONFIG}".toString()):"mygroup",
                ("kafka.consumers.${ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG}".toString()):"localhost:2222",
                ("kafka.${ConsumerConfig.GROUP_ID_CONFIG}".toString()):"mygroup",
                ("kafka.consumers.${ConsumerConfig.MAX_POLL_RECORDS_CONFIG}".toString()):"100"
        )

        when:
        KafkaConsumerConfiguration config = applicationContext.getBean(KafkaConsumerConfiguration)
        Properties props = config.getConfig()

        then:
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] == "localhost:2222"
        props[ConsumerConfig.GROUP_ID_CONFIG] == "mygroup"
        props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] == "100"

        when:
        KafkaConsumer consumer = applicationContext.createBean(KafkaConsumer, config)

        then:
        consumer != null

        cleanup:
        consumer.close()
        applicationContext.close()
    }
}
