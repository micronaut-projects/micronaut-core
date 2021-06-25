package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.micronaut.core.io.buffer.ReferenceCounted;

@Internal
public final class ByteBufferUtils {
    public static void safeRelease(@NonNull ByteBuffer<?> buffer) {
        Object o = buffer.asNativeBuffer();
        if (o instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) o;
            if (byteBuf.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(byteBuf);
            }
        }
    }

    public static void release(@NonNull HttpResponse<ByteBuffer<?>> byteBufferHttpResponse) {
        ByteBuffer<?> buffer = byteBufferHttpResponse.body();
        if (buffer instanceof ReferenceCounted) {
            ((ReferenceCounted) buffer).release();
        }
    }
}
