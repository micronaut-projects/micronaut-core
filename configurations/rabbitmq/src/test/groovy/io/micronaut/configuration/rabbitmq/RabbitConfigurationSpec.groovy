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

    /**
     * This test requires a running rabbit instance to be successful. Also while the test always passes you do not always
     * see the message come through (I have a suspicion that may be due to timing).
     */
//    void "default rabbit configuration with connection"() {
//        given:
//            ApplicationContext applicationContext = new DefaultApplicationContext("test")
//            applicationContext.start()
//
//        expect: "connection factory bean is available"
//            applicationContext.containsBean(ConnectionFactory)
//            applicationContext.containsBean(Connection)
//            applicationContext.containsBean(Channel)
//
//        when: "the connection factory is returned"
//            ConnectionFactory cf = applicationContext.getBean(ConnectionFactory)
//            Connection publisherConnection = applicationContext.getBean(Connection)
//            Channel publisherChannel = applicationContext.getBean(Channel)
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
//            Connection consumerConnection = applicationContext.getBean(Connection)
//            Channel consumerChannel = consumerConnection.createChannel()
//            consumerChannel.queueDeclare("hello", false, false, false, null)
//
//            Consumer consumer = new DefaultConsumer(consumerChannel) {
//                @Override
//                void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
//                    String message = new String(body, "UTF-8");
//                    System.out.println(" [x] Received '" + message + "'");
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
