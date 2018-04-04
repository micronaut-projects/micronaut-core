package io.micronaut.configuration.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import spock.lang.Specification

class RabbitConfigurationSpec extends Specification {

    void "default rabbit configuration"() {
        given:
            ApplicationContext applicationContext = new DefaultApplicationContext("test")
            applicationContext.start()

        expect: "connection factory bean is available"
            applicationContext.containsBean(ConnectionFactory)

        when: "when the connection factory is returned"
            ConnectionFactory cf = applicationContext.getBean(ConnectionFactory)

        then: "default configuration is available"
            cf.getUsername() == "guest"
            cf.getPassword() == "guest"
            cf.getVirtualHost() == "/"
            cf.getHost() == "localhost"
            cf.getPort() == 5672
            cf.getRequestedChannelMax() == 0
            cf.getRequestedFrameMax() == 0
            cf.getRequestedHeartbeat() == 60
            cf.getConnectionTimeout() == 60000
            cf.getHandshakeTimeout() == 10000
            cf.getShutdownTimeout() == 10000

        cleanup:
            applicationContext.close()
    }

    void "override default rabbit configuration"() {
        given:
            ApplicationContext applicationContext = new DefaultApplicationContext("test")
            applicationContext.start()

        expect: "connection factory bean is available"
            applicationContext.containsBean(ConnectionFactory)

        when: "when the connection factory is returned and values are overridden"
            ConnectionFactory cf = applicationContext.getBean(ConnectionFactory)
            cf.setHost("myNewHost")
            cf.setPort(9999)

        then: "overridden configuration is available"
            cf.getHost() == "myNewHost"
            cf.getPort() == 9999

        cleanup:
            applicationContext.close()
    }

    void "default rabbit configuration is used when configuration properties are empty"() {
        given:
            ApplicationContext applicationContext = new DefaultApplicationContext("test")
            applicationContext.environment.addPropertySource(MapPropertySource.of(
                    'test',
                    ['rabbitmq': [:]]
            ))
            applicationContext.start()

        expect: "connection factory bean is available"
            applicationContext.containsBean(ConnectionFactory)

        when: "when the connection factory is returned and values are overridden"
            ConnectionFactory cf = applicationContext.getBean(ConnectionFactory)

        then: "default configuration is available"
            cf.getUsername() == "guest"
            cf.getPassword() == "guest"
            cf.getVirtualHost() == "/"
            cf.getHost() == "localhost"
            cf.getPort() == 5672
            cf.getRequestedChannelMax() == 0
            cf.getRequestedFrameMax() == 0
            cf.getRequestedHeartbeat() == 60
            cf.getConnectionTimeout() == 60000
            cf.getHandshakeTimeout() == 10000
            cf.getShutdownTimeout() == 10000

        cleanup:
            applicationContext.close()
    }

    void "default rabbit configuration is overridden when configuration properties are passed in"() {
        given:
            ApplicationContext applicationContext = ApplicationContext.run(
                    ["rabbitmq.username": "guest1",
                     "rabbitmq.password": "guest1",
                     "rabbitmq.virtualHost": "/guest1",
                     "rabbitmq.host": "guesthost",
                     "rabbitmq.port": 9999,
                     "rabbitmq.requestedChannelMax": 50,
                     "rabbitmq.requestedFrameMax": 50,
                     "rabbitmq.requestedHeartbeat": 50,
                     "rabbitmq.connectionTimeout": 50,
                     "rabbitmq.handshakeTimeout": 50,
                     "rabbitmq.shutdownTimeout": 50],
                    "test"
            )

        expect: "connection factory bean is available"
            applicationContext.containsBean(ConnectionFactory)
            applicationContext.containsBean(RabbitConnectionFactoryConfig)

        when: "when the connection factory is returned and values are overridden"
            ConnectionFactory cf = applicationContext.getBean(RabbitConnectionFactoryConfig)

        then: "default configuration is available"
            cf.getUsername() == "guest1"
            cf.getPassword() == "guest1"
            cf.getVirtualHost() == "/guest1"
            cf.getHost() == "guesthost"
            cf.getPort() == 9999
            cf.getRequestedChannelMax() == 50
            cf.getRequestedFrameMax() == 50
            cf.getRequestedHeartbeat() == 50
            cf.getConnectionTimeout() == 50
            cf.getHandshakeTimeout() == 50
            cf.getShutdownTimeout() == 50

        cleanup:
            applicationContext.close()
    }

    /** ================================================================================================================
     * The following tests only run when a working rabbit instance is available
     * ============================================================================================================== */
//    void "default rabbit configuration is overridden when configuration properties are passed in and are available from a new connection"() {
//        given:
//        ApplicationContext applicationContext = ApplicationContext.run(
//                ["rabbitmq.username": "guest",
//                 "rabbitmq.password": "guest",
//                 "rabbitmq.virtualHost": "/",
//                 "rabbitmq.host": "localhost",
//                 "rabbitmq.port": 5672,
//                 "rabbitmq.requestedChannelMax": 50,
//                 "rabbitmq.requestedHeartbeat": 50,
//                 "rabbitmq.connectionTimeout": 50,
//                 "rabbitmq.handshakeTimeout": 50,
//                 "rabbitmq.shutdownTimeout": 50],
//                "test"
//        )
//
//        expect: "connection factory bean is available"
//        applicationContext.containsBean(ConnectionFactory)
//        applicationContext.containsBean(RabbitConnectionFactoryConfig)
//
//        when: "when the connection factory is returned and values are overridden"
//        ConnectionFactory cf = applicationContext.getBean(RabbitConnectionFactoryConfig)
//        Connection c = cf.newConnection()
//
//        then: "default configuration is available"
//        c.getAddress()
//        c.getPort() == 5672
//        c.getChannelMax() == 50
//        c.getHeartbeat() == 50
//
//        when:
//        Channel channel = c.createChannel()
//
//        then:
//        channel
//
//        cleanup:
//        c.close()
//        applicationContext.close()
//    }
//
//    void "default rabbit configuration with connection"() {
//        given:
//            ApplicationContext applicationContext = new DefaultApplicationContext("test")
//            applicationContext.start()
//
//        expect: "connection factory bean is available"
//            applicationContext.containsBean(RabbitConnectionFactoryConfig)
//
//        when: "the connection factory is returned"
//            ConnectionFactory cf = applicationContext.getBean(RabbitConnectionFactoryConfig)
//            Connection publisherConnection = cf.newConnection()
//            Channel publisherChannel = publisherConnection.createChannel()
//
//        then: "the rabbit beans are available"
//            cf
//            publisherConnection
//            publisherChannel
//
//        when: "the queue has been set"
//            publisherChannel.queueDeclare("hello", false, false, false, null)
//
//        then: "publish a message"
//            String originalMessage = "Hello World!"
//            publisherChannel.basicPublish("", "hello", null, originalMessage.getBytes("UTF-8"))
//
//        when: "a consumer is present"
//            // TODO: for some reason the output message is only available on the first run or if you edit this block, try adding a println
//            Connection consumerConnection = cf.newConnection()
//            Channel consumerChannel = consumerConnection.createChannel()
//            consumerChannel.queueDeclare("hello", false, false, false, null)
//
//            Consumer consumer = new DefaultConsumer(consumerChannel) {
//                @Override
//                void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
//                    String message = new String(body, "UTF-8")
//                    System.out.println(" [x] Received '" + message + "'")
//                }
//            }
//
//        then: "consume the message"
//            consumerChannel.basicConsume("hello", true, consumer)
//
//        cleanup:
//            applicationContext.close()
//    }
}
