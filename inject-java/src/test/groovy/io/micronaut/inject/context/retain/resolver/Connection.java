package io.micronaut.inject.context.retain.resolver;

/**
 * A connection: holds what it resolved, not the resolver.
 */
public final class Connection {
    public final Channel channel;
    public final Broker broker;

    Connection(Channel channel, Broker broker) {
        this.channel = channel;
        this.broker = broker;
    }
}
