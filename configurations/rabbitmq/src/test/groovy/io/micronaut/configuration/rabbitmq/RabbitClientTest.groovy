package io.micronaut.configuration.rabbitmq


import io.micronaut.configuration.rabbitmq.annotation.RabbitProperties
import io.micronaut.configuration.rabbitmq.annotation.Queue
import io.micronaut.configuration.rabbitmq.annotation.RabbitClient
import io.micronaut.configuration.rabbitmq.annotation.RabbitListener
import io.micronaut.configuration.rabbitmq.annotation.RoutingKey
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class RabbitClientTest extends Specification {

    @Shared
    @AutoCleanup
    GenericContainer rabbitContainer =
            new GenericContainer("library/rabbitmq:3.7")
                    .withExposedPorts(5672)
                    .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*Server startup complete.*"))

    void "test simple producing and consuming"() {
        rabbitContainer.start()
        ApplicationContext applicationContext = ApplicationContext.run(
                ["rabbitmq.port": rabbitContainer.getMappedPort(5672),
                "spec.name": getClass().simpleName], "test")
        PollingConditions conditions = new PollingConditions(timeout: 3)

        when:
        applicationContext.getBean(MyProducer).go("abc".bytes)

        then:
        conditions.eventually {
            applicationContext.getBean(MyConsumer).messages.size() == 1
            applicationContext.getBean(MyConsumer).messages[0] == "abc".bytes
        }
    }

    @Requires(property = "spec.name", value = "RabbitClientTest")
    @RabbitClient
    static interface MyProducer {

        @RoutingKey("abc")
        void go(@Body byte[] data)
    }

    @Requires(property = "spec.name", value = "RabbitClientTest")
    @RabbitListener
    static class MyConsumer {

        public static List<byte[]> messages = []

        @Queue("abc")
        void listen(byte[] data) {
            messages.add(data)
        }
    }
}
