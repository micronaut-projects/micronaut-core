package io.micronaut.http.server.netty.fuzzing;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.http.tck.netty.TestLeakDetector;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class FuzzyInputTest {
    @BeforeEach
    void setup() {
        FlagAppender.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        FlagAppender.checkTriggered();
        TestLeakDetector.reportStillOpen();
    }

    private static ByteBuf copiedBuffer(String s) {
        return ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, s);
    }

    @Test
    public void testFewer() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "FuzzyInputTest"))) {
            EmbeddedChannel channel = ((NettyHttpServer) ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
            channel.writeInbound(ByteBufUtil.writeUtf8(channel.alloc(), """
                POST /echo-piece-json HTTP/1.0
                Content-Length: 7
                Transfer-Encoding: chunked

                """));
            channel.writeInbound(ByteBufUtil.writeUtf8(channel.alloc(), "\n"));

            channel.finishAndReleaseAll();
            channel.checkException();
        }
    }

    @Test
    public void testMore() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "FuzzyInputTest"))) {
            EmbeddedChannel channel = ((NettyHttpServer) ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
            channel.writeInbound(ByteBufUtil.writeUtf8(channel.alloc(), """
                POST /echo-piece-json HTTP/1.0
                Content-Length: 7
                Transfer-Encoding: chunked

                C
                O:"""));
            channel.writeInbound(ByteBufUtil.writeUtf8(channel.alloc(), "aaaaaa"));

            channel.finishAndReleaseAll();
            channel.checkException();
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "FuzzyInputTest")
    static class Ctrl {
        @Post("/echo-piece-json")
        @Consumes({
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_FORM_URLENCODED
        })
        public String echoPieceJson(@Body("foo") String foo) {
            return foo;
        }
    }
}
