/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.micronaut.core.io.buffer.ReferenceCounted;

/**
 * Utility method to release Byte buffer reference count.
 * @author Sergio del Amo
 * @since 3.0.0
 */
@Internal
public final class ByteBufferUtils {
    /**
     * Uses {@link ReferenceCountUtil#safeRelease(Object)} to release buffer.
     * @param buffer Byte Buffer
     */
    public static void safeRelease(@NonNull ByteBuffer<?> buffer) {
        Object o = buffer.asNativeBuffer();
        if (o instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) o;
            if (byteBuf.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(byteBuf);
            }
        }
    }

    /**
     * If the body of the HTTP Response is {@link ReferenceCounted}, it releases it using {@link ReferenceCounted#release()}
     * @param byteBufferHttpResponse HTTP Response with the Byte Buffer as the body.
     */
    public static void release(@NonNull HttpResponse<ByteBuffer<?>> byteBufferHttpResponse) {
        ByteBuffer<?> buffer = byteBufferHttpResponse.body();
        if (buffer instanceof ReferenceCounted) {
            ((ReferenceCounted) buffer).release();
        }
    }
}
