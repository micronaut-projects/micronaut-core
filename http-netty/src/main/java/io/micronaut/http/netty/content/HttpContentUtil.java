/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.netty.content;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;

import java.nio.charset.StandardCharsets;

/**
 * Utility methods for generated HTTP content.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class HttpContentUtil {

    public static final byte[] OPEN_BRACKET = "[".getBytes(StandardCharsets.UTF_8);
    public static final byte[] CLOSE_BRACKET = "]".getBytes(StandardCharsets.UTF_8);
    public static final byte[] COMMA = ",".getBytes(StandardCharsets.UTF_8);

    /**
     * @return Produces HTTP content for {@code [}
     */
    public static HttpContent openBracket() {
        return new DefaultHttpContent(Unpooled.wrappedBuffer(OPEN_BRACKET));
    }

    /**
     * @return Produces HTTP content for {@code ]}
     */
    public static HttpContent closeBracket() {
        return new DefaultHttpContent(Unpooled.wrappedBuffer(CLOSE_BRACKET));
    }

    /**
     * @param httpContent The http content to prefix
     * @return Produces HTTP content for {@code ]}
     */
    public static HttpContent prefixComma(HttpContent httpContent) {
        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer(2);
        compositeByteBuf.addComponent(true, Unpooled.wrappedBuffer(COMMA));
        compositeByteBuf.addComponent(true, httpContent.content());
        return httpContent.replace(
            compositeByteBuf
        );
    }
}
