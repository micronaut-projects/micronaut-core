package io.micronaut.inject.context.retain.resolver;

/**
 * A connection: holds what it resolved, not the resolver.
 */
public final class Connection {
    public final Channel channel;
    public final Broker broker;
    public final int timeout;

    Connection(Channel channel, Broker broker, int timeout) {
        this.channel = channel;
        this.broker = broker;
        this.timeout = timeout;
    }
}
