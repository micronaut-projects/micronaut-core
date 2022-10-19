package io.micronaut.http.client.netty

import io.netty.channel.ChannelId

class DummyChannelId implements ChannelId {
    final String name

    DummyChannelId(String name) {
        this.name = name
    }

    @Override
    String asShortText() {
        return name
    }

    @Override
    String asLongText() {
        return name
    }

    @Override
    int compareTo(ChannelId o) {
        return asLongText() <=> o.asLongText()
    }
}
