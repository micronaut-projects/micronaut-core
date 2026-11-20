package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request whose {@code 101 Switching Protocols} response cannot be written is still cleaned up:
 * it is released and its {@link HttpRequestTerminatedEvent} is published, as when the switch
 * succeeds.
 */
class SwitchProtocolsWriteFailureTest {
    private static final String SPEC_NAME = "SwitchProtocolsWriteFailureTest";

    @Test
    void aRequestWhoseSwitchingProtocolsResponseFailsToWriteIsCleanedUp() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
             Socket socket = new Socket("localhost", server.getPort())) {
            Recorder recorder = server.getApplicationContext().getBean(Recorder.class);
            socket.getOutputStream().write(("GET /switch-failure/switch HTTP/1.1\r\nHost: localhost\r\n" +
                "Connection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            assertTrue(recorder.writeFailed.await(10, TimeUnit.SECONDS));
            assertTrue(recorder.upgradeClosed.await(10, TimeUnit.SECONDS));
            // the connection is closed
            socket.setSoTimeout(10_000);
            assertEquals(-1, readUntilEnd(socket));

            assertTrue(recorder.terminatedLatch.await(10, TimeUnit.SECONDS), "the request was not terminated");
            assertEquals(List.of("/switch-failure/switch"), recorder.terminated);
        }
    }

    private static int readUntilEnd(Socket socket) throws IOException {
        byte[] buf = new byte[1024];
        int n;
        do {
            n = socket.getInputStream().read(buf);
        } while (n > 0);
        return n;
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        final List<String> terminated = new CopyOnWriteArrayList<>();
        final CountDownLatch terminatedLatch = new CountDownLatch(1);
        final CountDownLatch writeFailed = new CountDownLatch(1);
        final CountDownLatch upgradeClosed = new CountDownLatch(1);

        @Override
        public void onApplicationEvent(HttpRequestTerminatedEvent event) {
            terminated.add(event.getSource().getPath());
            terminatedLatch.countDown();
        }
    }

    /**
     * Fails the write of every {@code 101} response on the connections of the server.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FailSwitchingProtocols implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        private final Recorder recorder;

        FailSwitchingProtocols(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.getBean().register(new NettyServerCustomizer() {
                @Override
                public NettyServerCustomizer specializeForChannel(Channel connection, ChannelRole role) {
                    if (role != ChannelRole.CONNECTION) {
                        return this;
                    }
                    return new NettyServerCustomizer() {
                        @Override
                        public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole r) {
                            return this;
                        }

                        @Override
                        public void onStreamPipelineBuilt() {
                            connection.pipeline().addLast("fail-switching-protocols", new ChannelOutboundHandlerAdapter() {
                                @Override
                                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                                    if (msg instanceof io.netty.handler.codec.http.HttpResponse response
                                        && response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
                                        ReferenceCountUtil.release(msg);
                                        promise.setFailure(new IOException("101 write failed"));
                                        recorder.writeFailed.countDown();
                                        return;
                                    }
                                    super.write(ctx, msg, promise);
                                }
                            });
                        }
                    };
                }
            });
            return event.getBean();
        }
    }

    @Controller("/switch-failure")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        private final Recorder recorder;

        Routes(Recorder recorder) {
            this.recorder = recorder;
        }

        @Get("/switch")
        @Produces(MediaType.ALL)
        HttpResponse<?> switchProtocols() {
            return new EchoUpgrade(recorder);
        }
    }

    static final class EchoUpgrade extends HttpResponseWrapper<Object> implements UpgradedHttpResponse<Object> {
        private final Recorder recorder;
        private final CloseableByteBody body = ByteBodyFactory.createDefault(NettyByteBufferFactory.DEFAULT)
            .adapt("UPGRADED".getBytes(StandardCharsets.US_ASCII));

        EchoUpgrade(Recorder recorder) {
            super(HttpResponse.status(HttpStatus.SWITCHING_PROTOCOLS)
                .header(HttpHeaders.UPGRADE, "echo")
                .header(HttpHeaders.CONNECTION, "Upgrade"));
            this.recorder = recorder;
        }

        @Override
        public void send(CloseableByteBody outbound) {
            outbound.close();
        }

        @Override
        public @Nullable String getProtocol() {
            return "echo";
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public void close() {
            body.close();
            recorder.upgradeClosed.countDown();
        }
    }
}
