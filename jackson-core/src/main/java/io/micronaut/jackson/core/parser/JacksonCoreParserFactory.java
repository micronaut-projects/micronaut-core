package io.micronaut.jackson.core.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class for implementing
 * {@link io.micronaut.json.JsonMapper#readValue(ByteBuffer, Argument)} with optimizations for
 * netty ByteBufs.
 */
@Internal
public class JacksonCoreParserFactory {
    private static final boolean HAS_NETTY_BUFFER;

    private JacksonCoreParserFactory() {}

    static {
        boolean hasNettyBuffer;
        try {
            Class.forName("io.netty.buffer.ByteBuf", false, null);
            hasNettyBuffer = true;
        } catch (ClassNotFoundException e) {
            hasNettyBuffer = false;
        }
        HAS_NETTY_BUFFER = hasNettyBuffer;
    }

    public static JsonParser createJsonParser(JsonFactory factory, ByteBuffer<?> buffer) throws IOException {
        if (!HAS_NETTY_BUFFER || !(buffer.asNativeBuffer() instanceof ByteBuf byteBuf)) {
            return factory.createParser(buffer.toByteArray());
        }

        if (byteBuf.hasArray()) {
            return factory.createParser(byteBuf.array(), byteBuf.readerIndex() + byteBuf.arrayOffset(), byteBuf.readableBytes());
        } else {
            return factory.createParser((InputStream) new ByteBufInputStream(byteBuf));
        }
    }
}
