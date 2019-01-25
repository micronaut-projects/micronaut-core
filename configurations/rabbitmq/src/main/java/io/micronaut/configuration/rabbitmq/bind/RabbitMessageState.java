package io.micronaut.configuration.rabbitmq.bind;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

public class RabbitMessageState {

    private final Envelope envelope;
    private final AMQP.BasicProperties properties;
    private final byte[] body;

    public RabbitMessageState(Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        this.envelope = envelope;
        this.properties = properties;
        this.body = body;
    }

    public byte[] getBody() {
        return body;
    }

    public AMQP.BasicProperties getProperties() {
        return properties;
    }

    public Envelope getEnvelope() {
        return envelope;
    }
}
