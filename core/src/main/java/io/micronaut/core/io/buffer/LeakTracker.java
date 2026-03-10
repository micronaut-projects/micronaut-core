/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.io.buffer;

import io.netty.util.LeakPresenceDetector;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Platform-agnostic leak tracking API. Currently only implemented when netty is available.
 *
 * @param <T> Type to track
 * @since 5.0.0
 */
public interface LeakTracker<T> {
    /**
     * Close this tracker. May be called multiple times (idempotent).
     *
     * @param trackedObject The tracked object, must match the one passed to
     * {@link Factory#track(Object)}
     */
    void close(T trackedObject);

    /**
     * Factory.
     *
     * @param <T> Type to track
     */
    interface Factory<T> {
        /**
         * Create a new leak tracker.
         *
         * @param object The tracked object
         * @return The leak tracker, o {@code null} if this object should not be tracked
         */
        @Nullable
        LeakTracker<T> track(T object);

        /**
         * Create a new leak tracker factory for the given tracked object type.
         *
         * @param trackedClass The type to track
         * @param <T> The tracked type
         * @return The tracker factory
         */
        static <T> Factory<T> forClass(Class<T> trackedClass) {
            if (LeakTrackerFactoryHolder.nettyAvailable) {
                try {
                    return new NettyLeakTrackerFactory<>(trackedClass);
                } catch (LinkageError err) {
                    LeakTrackerFactoryHolder.nettyAvailable = false;
                }
            }
            // fallback: no tracking
            return obj -> null;
        }

        /**
         * Wrap static leak tracker creation to avoid triggering the LeakPresenceDetector. See
         * netty javadoc.
         *
         * @param supplier Supplier to call
         * @param <R> The supplier return value
         * @return Value returned by the supplier
         * @see io.netty.util.LeakPresenceDetector#staticInitializer(Supplier)
         */
        static <R> R staticInitializer(Supplier<R> supplier) {
            if (LeakTrackerFactoryHolder.nettyAvailable) {
                return LeakPresenceDetector.staticInitializer(supplier);
            } else {
                return supplier.get();
            }
        }
    }
}

class LeakTrackerFactoryHolder {
    static boolean nettyAvailable = true;
}

@SuppressWarnings({"ReturnValueIgnored", "ResultOfMethodCallIgnored"})
final class NettyLeakTrackerFactory<T> implements LeakTracker.Factory<T> {
    private final Class<T> trackedClass;
    @Nullable
    private volatile ResourceLeakDetector<T> detector;

    static {
        // only allow initializing this class if netty is available
        ResourceLeakDetector.class.getName();
    }

    NettyLeakTrackerFactory(Class<T> trackedClass) {
        this.trackedClass = trackedClass;
    }

    @Override
    public @Nullable LeakTracker<T> track(T object) {
        ResourceLeakDetector<T> detector = this.detector;
        if (detector == null) {
            synchronized (this) {
                detector = this.detector;
                if (detector == null) {
                    detector = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(trackedClass);
                    this.detector = detector;
                }
            }
        }
        ResourceLeakTracker<T> nettyTracker = detector.track(object);
        if (nettyTracker != null) {
            return new NettyLeakTracker<>(nettyTracker);
        } else {
            return null;
        }
    }

    private record NettyLeakTracker<T>(ResourceLeakTracker<T> tracker) implements LeakTracker<T> {
        @Override
        public void close(T trackedObject) {
            tracker.close(trackedObject);
        }
    }
}
