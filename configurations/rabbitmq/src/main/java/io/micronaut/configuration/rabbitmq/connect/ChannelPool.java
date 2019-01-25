package io.micronaut.configuration.rabbitmq.connect;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.LinkedList;

@Singleton
public class ChannelPool implements AutoCloseable {

    private final LinkedList<Channel> channels = new LinkedList<>();
    private final Connection connection;

    public ChannelPool(Connection connection) {
        this.connection = connection;
    }

    public Channel getChannel() throws IOException {
        Channel channel = null;
        synchronized (channels) {
            while (!channels.isEmpty()) {
                channel = channels.removeFirst();
                if (channel.isOpen()) {
                    break;
                } else {
                    channel = null;
                }
            }
        }
        if (channel == null) {
            channel = createChannel();
        }

        return channel;
    }

    public void returnChannel(Channel channel) {
        if (channel.isOpen()) {
            synchronized (channels) {
                if (!channels.contains(channel)) {
                    channels.addLast(channel);
                }
            }
        }
    }

    protected Channel createChannel() throws IOException {
        return connection.createChannel();
    }

    @Override
    public void close() throws Exception {
        synchronized (channels) {
            while (!channels.isEmpty()) {
                Channel channel = channels.removeFirst();
                channel.close();
            }
        }
    }
}
