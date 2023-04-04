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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpContent;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully buffered {@link ByteBody}, all operations are eager.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class ImmediateByteBody extends ManagedBody<ByteBuf> implements ByteBody {
    private final boolean empty;

    ImmediateByteBody(ByteBuf buf) {
        super(buf);
        this.empty = !buf.isReadable();
    }

    @Override
    void release(ByteBuf value) {
        value.release();
    }

    @Override
    public MultiObjectBody processMulti(HttpContentProcessor processor) throws Throwable {
        ByteBuf data = prepareClaim();
        Object item = processor.processSingle(data);
        if (item != null) {
            return next(new ImmediateSingleObjectBody(item));
        }

        return next(processMultiImpl(processor, data));
    }

    @NotNull
    private ImmediateMultiObjectBody processMultiImpl(HttpContentProcessor processor, ByteBuf data) throws Throwable {
        List<Object> out = new ArrayList<>(1);
        if (data.isReadable()) {
            processor.add(new DefaultHttpContent(data), out);
        } else {
            data.release();
        }
        processor.complete(out);
        return new ImmediateMultiObjectBody(out);
    }

    /**
     * Process this body and then transform it into a single object using
     * {@link ImmediateMultiObjectBody#single}.
     *
     * @param processor The processor
     * @param defaultCharset The default charset (see {@link ImmediateMultiObjectBody#single})
     * @param alloc The buffer allocator (see {@link ImmediateMultiObjectBody#single})
     * @return The processed object
     * @throws Throwable Any failure
     */
    public ImmediateSingleObjectBody processSingle(HttpContentProcessor processor, Charset defaultCharset, ByteBufAllocator alloc) throws Throwable {
        return next(processMultiImpl(processor, prepareClaim()).single(defaultCharset, alloc));
    }

    @Override
    public ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc) {
        return ExecutionFlow.just(this);
    }

    public boolean empty() {
        return empty;
    }
}
