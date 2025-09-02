/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.AbstractBodyAdapter;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.netty.EventLoopFlow;
import io.netty.channel.EventLoop;
import org.reactivestreams.Publisher;

/**
 * Adapter from generic streaming {@link ByteBody} to {@link StreamingNettyByteBody}.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Internal
final class NettyBodyAdapter extends AbstractBodyAdapter {
    private final EventLoopFlow eventLoopFlow;

    NettyBodyAdapter(EventLoop eventLoop, Publisher<ReadBuffer> source, @Nullable Runnable onDiscard) {
        super(source, onDiscard);
        this.eventLoopFlow = new EventLoopFlow(eventLoop);
    }

    @Override
    public void onNext(ReadBuffer bytes) {
        if (eventLoopFlow.executeNow(() -> super.onNext(bytes))) {
            super.onNext(bytes);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (eventLoopFlow.executeNow(() -> super.onError(t))) {
            super.onError(t);
        }
    }

    @Override
    public void onComplete() {
        if (eventLoopFlow.executeNow(super::onComplete)) {
            super.onComplete();
        }
    }

}
