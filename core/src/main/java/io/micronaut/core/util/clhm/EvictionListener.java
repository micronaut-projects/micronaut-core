/*
 * Copyright 2010 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.core.util.clhm;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A listener registered for notification when an entry is evicted. An instance
 * may be called concurrently by multiple threads to process entries. An
 * implementation should avoid performing blocking calls or synchronizing on
 * shared resources.
 * <p>
 * The listener is invoked by {@link ConcurrentLinkedHashMap} on a caller's
 * thread and will not block other threads from operating on the map. An
 * implementation should be aware that the caller's thread will not expect
 * long execution times or failures as a side effect of the listener being
 * notified. Execution safety and a fast turn around time can be achieved by
 * performing the operation asynchronously, such as by submitting a task to an
 * {@link java.util.concurrent.ExecutorService}.
 *
 * @param <K> The key
 * @param <V> The value
 * @author ben.manes@gmail.com (Ben Manes)
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *      http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
@ThreadSafe
public interface EvictionListener<K, V> {

    /**
     * A call-back notification that the entry was evicted.
     *
     * @param key the entry's key
     * @param value the entry's value
     */
    void onEviction(K key, V value);
}
