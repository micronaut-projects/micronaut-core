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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/**
 * Non-thread-safe queue for bytes.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Internal
final class ByteQueue {
    // originally from micronaut-servlet
    // not the most efficient implementation, but the most readable.

    private final Queue<ByteBuffer> queue = new ArrayDeque<>();

    /**
     * Add a copy of the given array to this queue.
     *
     * @param arr The input array
     * @param off The offset of the section to add
     * @param len The length of the section to add
     */
    public void addCopy(byte[] arr, int off, int len) {
        add(Arrays.copyOfRange(arr, off, off + len));
    }

    private void add(byte[] arr) {
        if (arr.length == 0) {
            return;
        }
        queue.add(ByteBuffer.wrap(arr));
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int take(byte[] arr, int off, int len) {
        ByteBuffer peek = queue.peek();
        if (peek == null) {
            throw new IllegalStateException("Queue is empty");
        }
        int n = Math.min(len, peek.remaining());
        peek.get(arr, off, n);
        if (peek.remaining() == 0) {
            queue.poll();
        }
        return n;
    }

    public void clear() {
        queue.clear();
    }
}
