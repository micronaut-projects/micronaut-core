/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.netty.util.concurrent.EventExecutor;

/**
 * Information about what threads are blocked waiting for a request to complete. This is used to
 * detect deadlocks when the user does a {@link io.micronaut.http.client.BlockingHttpClient} on the
 * event loop.<br>
 * Note: This class is public for use in micronaut-oracle-cloud.
 *
 * @param blockedThread Thread that is blocked
 * @param next Next node in the linked list of blocked threads
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public record BlockHint(Thread blockedThread, @Nullable BlockHint next) {
    public static BlockHint willBlockThisThread() {
        return new BlockHint(Thread.currentThread(), null);
    }

    @Nullable
    public static BlockHint combine(@Nullable BlockHint a, @Nullable BlockHint b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else if (a.next == null) {
            return new BlockHint(a.blockedThread, b);
        } else if (b.next == null) {
            return new BlockHint(b.blockedThread, a);
        } else {
            throw new UnsupportedOperationException(
                "would need to build a new linked list here, but we never need this");
        }
    }

    @NonNull
    static HttpClientException createException() {
        return new HttpClientException(
            "Failed to perform blocking request on the event loop because request execution " +
                "would be dispatched on the same event loop. This would lead to a deadlock. " +
                "Either configure the HTTP client to use a different event loop, or use the " +
                "reactive HTTP client. " +
                "https://docs.micronaut.io/latest/guide/index.html#clientConfiguration");
    }

    boolean blocks(EventExecutor eventLoop) {
        BlockHint bh = this;
        while (bh != null) {
            if (eventLoop.inEventLoop(bh.blockedThread)) {
                return true;
            }
            bh = bh.next;
        }
        return false;
    }
}
