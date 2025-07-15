package io.micronaut.http.netty.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NettyByteBodyFactoryTest {
    static List<ByteBodyFactory> factories() {
        return List.of(
            ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE),
            new NettyByteBodyFactory(new EmbeddedChannel()));
    }

    @ParameterizedTest
    @MethodSource("factories")
    public void outputStreamBuffer(ByteBodyFactory factory) throws IOException {
        CloseableAvailableByteBody body;
        try (ByteBodyFactory.BufferingOutputStream bos = factory.outputStreamBuffer()) {
            bos.stream().write("foo".getBytes());
            body = bos.finishBuffer();
        }
        assertEquals("foo", body.toString(StandardCharsets.UTF_8));
        body.close();
    }
}
