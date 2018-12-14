package io.micronaut.configuration.kafka.annotation

import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.configuration.kafka.exceptions.KafkaListenerException
import io.micronaut.configuration.kafka.exceptions.KafkaListenerExceptionHandler
import io.micronaut.configuration.kafka.metrics.KafkaConsumerMetrics
import io.micronaut.configuration.kafka.metrics.KafkaProducerMetrics
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.util.CollectionUtils
import io.micronaut.messaging.annotation.Header
import io.reactivex.Single
import org.apache.kafka.clients.producer.RecordMetadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ConcurrentHashMap

@Stepwise
class KafkaTypeConversionSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            CollectionUtils.mapOf(
                    "kafka.bootstrap.servers", 'localhost:${random.port}',
                    AbstractKafkaConfiguration.EMBEDDED, true,
                    AbstractKafkaConfiguration.EMBEDDED_TOPICS, ["uuids"]
            )
    )

    void "test send valid UUID key"() {
        when:
        MyClient myClient = context.getBean(MyClient)
        def uuid = UUID.randomUUID()
        myClient.send(uuid.toString(), "test")

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        MyListener myConsumer = context.getBean(MyListener)

        then:
        conditions.eventually {
            myConsumer.messages[uuid] == 'test'
        }
    }

    void "test send invalid UUID key"() {
        when:
        MyClient myClient = context.getBean(MyClient)
        def uuid = "bunch 'o junk"
        myClient.send(uuid, "test")

        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        MyListener myConsumer = context.getBean(MyListener)

        then:
        conditions.eventually {
            myConsumer.lastException != null
            myConsumer.lastException.cause instanceof ConversionErrorException
            myConsumer.lastException.cause.message == 'Failed to convert argument [key] for value [bunch \'o junk] due to: Invalid UUID string: bunch \'o junk'
        }
    }

    @KafkaListener(groupId = "MyUuidGroup", offsetReset = OffsetReset.EARLIEST)
    static class MyListener implements KafkaListenerExceptionHandler {

        Map<UUID, String> messages = new ConcurrentHashMap<>()
        KafkaListenerException lastException

        @Topic(patterns = "uuids")
        void receive(@KafkaKey UUID key, String message) {
            messages.put(key, message)
        }

        @Override
        void handle(KafkaListenerException exception) {
            lastException = exception
        }
    }

    @KafkaClient
    static interface MyClient {
        @Topic("uuids")
        void send(@KafkaKey String key, String message)
    }
}
