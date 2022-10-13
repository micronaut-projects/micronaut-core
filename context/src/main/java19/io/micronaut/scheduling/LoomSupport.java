/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class LoomSupport {
    private static final boolean supported;
    private static Throwable failure;

    static {
        boolean s;
        try {
            //noinspection ResultOfMethodCallIgnored
            Thread.ofVirtual();
            s = true;
        } catch (UnsupportedOperationException | NoSuchMethodError e) {
            s = false;
            failure = e;
        }
        supported = s;
    }

    private LoomSupport() {}

    public static boolean isSupported() {
        return supported;
    }

    public static void checkSupported() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Virtual threads are not supported on this JVM, you may have to pass --enable-preview", failure);
        }
    }

    public static ExecutorService newThreadPerTaskExecutor(ThreadFactory threadFactory) {
        checkSupported();
        return Executors.newThreadPerTaskExecutor(threadFactory);
    }

    public static ThreadFactory newVirtualThreadFactory(String namePrefix) {
        checkSupported();
        return Thread.ofVirtual()
            .name(namePrefix, 1)
            .factory();
    }
}
