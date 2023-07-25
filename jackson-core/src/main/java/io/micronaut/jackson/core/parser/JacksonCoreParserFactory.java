/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public final class JacksonCoreParserFactory {
    private static final boolean HAS_NETTY_BUFFER;

    private JacksonCoreParserFactory() {
    }

    static {
        boolean hasNettyBuffer;
        try {
            Class.forName("io.netty.buffer.ByteBuf", false, JacksonCoreParserFactory.class.getClassLoader());
            hasNettyBuffer = true;
        } catch (ClassNotFoundException e) {
            hasNettyBuffer = false;
        }
        HAS_NETTY_BUFFER = hasNettyBuffer;
    }

    /**
     * Create a jackson {@link JsonParser} for the given input bytes.
     *
     * @param factory The jackson {@link JsonFactory} for parse features
     * @param buffer  The input data
     * @return The created parser
     * @throws IOException On failure of jackson createParser methods
     */
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
